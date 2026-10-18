package app.divisorcuentas.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.DivisorCuenta
import app.divisorcuentas.core.Item
import app.divisorcuentas.core.Precios
import app.divisorcuentas.llm.ModeloLocal
import app.divisorcuentas.llm.MotorAiEdge
import app.divisorcuentas.llm.MotorLlm
import app.divisorcuentas.llm.MotorMock
import app.divisorcuentas.llm.extraerItems
import app.divisorcuentas.ocr.OcrBoleta
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

enum class Pantalla { BOLETA, ITEMS, ASIGNAR, RESUMEN }

/** Ítem tal como se edita en pantalla (texto libre); se valida al calcular. */
data class ItemEditable(
    val id: Long,
    val nombre: String,
    val cantidad: String,
    val precio: String,
) {
    fun aItem(): Item? {
        val p = Precios.parsear(precio) ?: return null
        val c = cantidad.toIntOrNull()?.takeIf { it > 0 } ?: 1
        return Item(nombre.ifBlank { "Ítem" }, p, c)
    }
}

/**
 * Estado de toda la app: foto -> texto (OCR) -> ítems (Gemma) -> asignación -> totales.
 */
class CuentaViewModel(app: Application) : AndroidViewModel(app) {

    private val ocr = OcrBoleta(app)
    private val ids = AtomicLong()

    var pantalla by mutableStateOf(Pantalla.BOLETA)
    var ocupado by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var textoOcr by mutableStateOf("")
        private set

    // Motor del modelo
    var usarGemma by mutableStateOf(false)
        private set
    var archivoModelo by mutableStateOf(ModeloLocal.actual(app))
        private set
    private var motorGemma: MotorLlm? = null

    // Datos de la cuenta
    val items = mutableStateListOf<ItemEditable>()
    val participantes = mutableStateListOf<String>()
    /** id del ítem -> participantes que lo consumieron. Vacío = todos. */
    val asignaciones = mutableStateMapOf<Long, Set<String>>()

    var propinaPct by mutableStateOf("10")
    var impuestoIncluido by mutableStateOf(true)
    var impuestoPct by mutableStateOf("19")
    var conCentavos by mutableStateOf(false)

    // ---- Modelo ----

    fun elegirMotor(gemma: Boolean) {
        if (gemma && archivoModelo == null) {
            error = "Primero importa el archivo .task de Gemma."
            return
        }
        usarGemma = gemma
    }

    fun importarModelo(uri: Uri) = trabajar("Copiando el modelo (puede tardar)…") {
        motorGemma?.cerrar(); motorGemma = null
        archivoModelo = ModeloLocal.importar(getApplication(), uri)
        usarGemma = true
    }

    private fun motor(): MotorLlm {
        if (!usarGemma) return MotorMock()
        return motorGemma ?: MotorAiEdge(getApplication(), requireNotNull(archivoModelo)).also { motorGemma = it }
    }

    // ---- Boleta ----

    fun procesarFoto(uri: Uri) = trabajar("Leyendo la boleta…") {
        textoOcr = ocr.extraerTexto(uri)
        if (textoOcr.isBlank()) {
            throw IllegalStateException("No se encontró texto en la foto. Prueba con más luz y la boleta recta.")
        }
        ocupado = if (usarGemma) "Gemma está ordenando los ítems…" else "Ordenando los ítems…"
        val extraidos = extraerItems(motor(), textoOcr)
        items.clear(); asignaciones.clear()
        extraidos.forEach { items.add(it.editable()) }
        if (extraidos.isEmpty()) error = "No se reconocieron ítems; agrégalos a mano."
        pantalla = Pantalla.ITEMS
    }

    fun empezarSinFoto() {
        items.clear(); asignaciones.clear(); textoOcr = ""
        pantalla = Pantalla.ITEMS
    }

    // ---- Ítems ----

    fun agregarItem() {
        items.add(ItemEditable(ids.incrementAndGet(), "", "1", ""))
    }

    fun editarItem(nuevo: ItemEditable) {
        val i = items.indexOfFirst { it.id == nuevo.id }
        if (i >= 0) items[i] = nuevo
    }

    fun borrarItem(id: Long) {
        items.removeAll { it.id == id }
        asignaciones.remove(id)
    }

    // ---- Participantes ----

    fun agregarParticipante(nombre: String) {
        val n = nombre.trim()
        if (n.isNotEmpty() && n !in participantes) participantes.add(n)
    }

    fun borrarParticipante(nombre: String) {
        participantes.remove(nombre)
        asignaciones.keys.toList().forEach { id -> asignaciones[id] = asignaciones.getValue(id) - nombre }
    }

    fun alternar(itemId: Long, persona: String) {
        val actual = asignaciones[itemId].orEmpty()
        asignaciones[itemId] = if (persona in actual) actual - persona else actual + persona
    }

    // ---- Cálculo ----

    /** Ítems que no se pueden leer (precio inválido). */
    val itemsInvalidos: List<ItemEditable> get() = items.filter { it.aItem() == null }

    fun calcular(): Map<String, Detalle>? {
        if (participantes.isEmpty() || items.isEmpty() || itemsInvalidos.isNotEmpty()) return null
        val validos = items.map { it.aItem()!! }
        val porIndice = items.mapIndexed { i, it -> i to asignaciones[it.id].orEmpty().toList() }.toMap()
        return runCatching {
            DivisorCuenta.dividir(
                validos, porIndice, participantes.toList(),
                propinaPct = Precios.parsear(propinaPct) ?: BigDecimal.ZERO,
                impuestoPct = Precios.parsear(impuestoPct) ?: BigDecimal.ZERO,
                impuestoIncluido = impuestoIncluido,
                unidad = if (conCentavos) BigDecimal("0.01") else BigDecimal.ONE,
            )
        }.onFailure { error = it.message }.getOrNull()
    }

    fun totalBoleta(): BigDecimal = DivisorCuenta.totalBoleta(items.mapNotNull { it.aItem() })

    // ---- Utilidades ----

    private fun Item.editable() = ItemEditable(
        ids.incrementAndGet(), nombre, cantidad.toString(), precio.stripTrailingZeros().toPlainString(),
    )

    private fun trabajar(mensaje: String, bloque: suspend () -> Unit) {
        error = null
        ocupado = mensaje
        viewModelScope.launch {
            try { bloque() } catch (e: Throwable) { error = e.message ?: e.toString() } finally { ocupado = null }
        }
    }

    override fun onCleared() {
        ocr.cerrar()
        motorGemma?.cerrar()
    }
}
