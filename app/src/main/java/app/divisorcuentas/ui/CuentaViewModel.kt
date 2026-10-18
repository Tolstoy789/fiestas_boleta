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
import app.divisorcuentas.core.ArchivoHistorial
import app.divisorcuentas.core.CuentaGuardada
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.DivisorCuenta
import app.divisorcuentas.core.ExtraccionItems
import app.divisorcuentas.core.Grupo
import app.divisorcuentas.core.Historial
import app.divisorcuentas.core.Item
import app.divisorcuentas.core.ItemGuardado
import app.divisorcuentas.core.Precios
import app.divisorcuentas.core.Saldos
import app.divisorcuentas.core.Transferencia
import app.divisorcuentas.historial.RepositorioHistorial
import app.divisorcuentas.llm.ModeloLocal
import app.divisorcuentas.llm.MotorAiEdge
import app.divisorcuentas.llm.MotorLlm
import app.divisorcuentas.llm.MotorMock
import app.divisorcuentas.llm.extraerItems
import app.divisorcuentas.ocr.OcrBoleta
import app.divisorcuentas.ocr.cargarFotoReducida
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class Pantalla { BOLETA, ITEMS, ASIGNAR, RESUMEN, HISTORIAL }

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

    val unidades: Int get() = cantidad.toIntOrNull()?.takeIf { it > 0 } ?: 1
}

/**
 * Estado de toda la app: foto -> texto (OCR) -> ítems (Gemma) -> asignación -> totales,
 * más el historial de cuentas y los grupos de amigos.
 */
class CuentaViewModel(app: Application) : AndroidViewModel(app) {

    private val ocr = OcrBoleta(app)
    private val repositorio = RepositorioHistorial(app)
    private val ids = AtomicLong()

    var pantalla by mutableStateOf(Pantalla.BOLETA)
    var ocupado by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<String?>(null)
    var textoOcr by mutableStateOf("")
        private set

    /** El TOTAL que trae impresa la boleta, si el OCR lo leyó. */
    var totalImpreso by mutableStateOf<BigDecimal?>(null)
        private set

    // Motor del modelo
    var usarGemma by mutableStateOf(false)
        private set
    /** Mandarle a Gemma la foto además del texto (si el modelo tiene visión). */
    var gemmaLeeFoto by mutableStateOf(true)
    var archivoModelo by mutableStateOf(ModeloLocal.actual(app))
        private set
    private var motorGemma: MotorLlm? = null

    // Datos de la cuenta
    val items = mutableStateListOf<ItemEditable>()
    val participantes = mutableStateListOf<String>()
    /** id del ítem -> unidades que consumió cada participante. Vacío = todos por igual. */
    val asignaciones = mutableStateMapOf<Long, Map<String, Int>>()

    var propinaPct by mutableStateOf("10")
    var impuestoIncluido by mutableStateOf(true)
    var impuestoPct by mutableStateOf("19")
    var conCentavos by mutableStateOf(false)
    /** Quién pagó la cuenta completa; null si aún nadie. */
    var pagador by mutableStateOf<String?>(null)

    // Historial
    var historial by mutableStateOf(ArchivoHistorial())
        private set
    var tituloCuenta by mutableStateOf("")
    /** Id de la cuenta abierta del historial, para que volver a guardar la reemplace. */
    private var cuentaActual: Long? = null

    init {
        viewModelScope.launch { historial = repositorio.leer() }
    }

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
        totalImpreso = ExtraccionItems.totalImpreso(textoOcr)
        val motor = motor()
        val foto = if (usarGemma && gemmaLeeFoto) cargarFotoReducida(getApplication(), uri) else null
        ocupado = when {
            !usarGemma -> "Ordenando los ítems…"
            foto != null -> "Gemma está mirando la boleta…"
            else -> "Gemma está ordenando los ítems…"
        }
        val extraidos = extraerItems(motor, textoOcr, foto)
        nuevaCuenta()
        extraidos.forEach { items.add(it.editable()) }
        if (extraidos.isEmpty()) error = "No se reconocieron ítems; agrégalos a mano."
        pantalla = Pantalla.ITEMS
    }

    fun empezarSinFoto() {
        textoOcr = ""
        totalImpreso = null
        nuevaCuenta()
        pantalla = Pantalla.ITEMS
    }

    /** Limpia los ítems y asignaciones; los participantes se mantienen para la próxima ronda. */
    private fun nuevaCuenta() {
        items.clear(); asignaciones.clear()
        pagador = pagador?.takeIf { it in participantes }
        cuentaActual = null
        tituloCuenta = ""
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

    /**
     * Diferencia entre el total impreso y la suma de los ítems, o null si cuadran
     * o no se leyó el total.
     */
    fun diferenciaConImpreso(): BigDecimal? {
        val impreso = totalImpreso ?: return null
        val dif = impreso - totalBoleta()
        return dif.takeIf { it.abs() >= BigDecimal.ONE }
    }

    // ---- Participantes y grupos ----

    fun agregarParticipante(nombre: String) {
        val n = nombre.trim()
        if (n.isNotEmpty() && participantes.none { it.equals(n, ignoreCase = true) }) participantes.add(n)
    }

    fun borrarParticipante(nombre: String) {
        participantes.remove(nombre)
        asignaciones.keys.toList().forEach { id -> asignaciones[id] = asignaciones.getValue(id) - nombre }
        if (pagador == nombre) pagador = null
    }

    fun usarGrupo(grupo: Grupo) = grupo.miembros.forEach(::agregarParticipante)

    fun guardarGrupo(nombre: String) {
        if (nombre.isBlank() || participantes.isEmpty()) return
        actualizarHistorial { Historial.guardarGrupo(it, Grupo(nombre, participantes.toList())) }
        aviso = "Grupo \"${nombre.trim()}\" guardado."
    }

    fun borrarGrupo(nombre: String) = actualizarHistorial { Historial.borrarGrupo(it, nombre) }

    // ---- Asignación ----

    /** Marca o desmarca a [persona] en el ítem. Al marcarla, parte con 1 unidad. */
    fun alternar(itemId: Long, persona: String) {
        val actual = asignaciones[itemId].orEmpty()
        asignaciones[itemId] = if (persona in actual) actual - persona else actual + (persona to 1)
    }

    /** Suma o resta unidades a [persona] en un ítem con varias unidades (mínimo 1). */
    fun ajustarUnidades(itemId: Long, persona: String, delta: Int) {
        val actual = asignaciones[itemId].orEmpty()
        val unidades = (actual[persona] ?: return) + delta
        if (unidades >= 1) asignaciones[itemId] = actual + (persona to unidades)
    }

    // ---- Cálculo ----

    /** Ítems que no se pueden leer (precio inválido). */
    val itemsInvalidos: List<ItemEditable> get() = items.filter { it.aItem() == null }

    fun calcular(): Map<String, Detalle>? {
        if (participantes.isEmpty() || items.isEmpty() || itemsInvalidos.isNotEmpty()) return null
        val validos = items.map { it.aItem()!! }
        return runCatching {
            DivisorCuenta.dividirPorPartes(
                validos, partesPorIndice(), participantes.toList(),
                propinaPct = Precios.parsear(propinaPct) ?: BigDecimal.ZERO,
                impuestoPct = Precios.parsear(impuestoPct) ?: BigDecimal.ZERO,
                impuestoIncluido = impuestoIncluido,
                unidad = if (conCentavos) BigDecimal("0.01") else BigDecimal.ONE,
            )
        }.onFailure { error = it.message }.getOrNull()
    }

    fun transferencias(reparto: Map<String, Detalle>): List<Transferencia> {
        val quien = pagador ?: return emptyList()
        return Saldos.pagoUnico(reparto.mapValues { it.value.total }, quien)
    }

    fun totalBoleta(): BigDecimal = DivisorCuenta.totalBoleta(items.mapNotNull { it.aItem() })

    private fun partesPorIndice(): Map<Int, Map<String, Int>> =
        items.mapIndexed { i, it -> i to asignaciones[it.id].orEmpty() }.toMap()

    // ---- Historial ----

    fun guardarEnHistorial(reparto: Map<String, Detalle>) {
        val creada = cuentaActual ?: System.currentTimeMillis()
        val titulo = tituloCuenta.trim().ifEmpty {
            "Cuenta del " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("es-CL")).format(Date(creada))
        }
        val cuenta = CuentaGuardada(
            creada = creada,
            titulo = titulo,
            items = items.map { ItemGuardado(it.nombre, it.unidades, it.precio) },
            participantes = participantes.toList(),
            partes = partesPorIndice(),
            propinaPct = propinaPct,
            impuestoIncluido = impuestoIncluido,
            impuestoPct = impuestoPct,
            conCentavos = conCentavos,
            pagador = pagador,
            totales = reparto.mapValues { it.value.total.toPlainString() },
        )
        cuentaActual = creada
        tituloCuenta = titulo
        actualizarHistorial { Historial.agregarCuenta(it, cuenta) }
        aviso = "Guardada en el historial."
    }

    fun abrirDelHistorial(cuenta: CuentaGuardada) {
        items.clear(); asignaciones.clear(); participantes.clear()
        participantes.addAll(cuenta.participantes)
        cuenta.items.forEachIndexed { i, guardado ->
            val editable = ItemEditable(ids.incrementAndGet(), guardado.nombre, guardado.cantidad.toString(), guardado.precio)
            items.add(editable)
            cuenta.partes[i]?.let { asignaciones[editable.id] = it }
        }
        propinaPct = cuenta.propinaPct
        impuestoIncluido = cuenta.impuestoIncluido
        impuestoPct = cuenta.impuestoPct
        conCentavos = cuenta.conCentavos
        pagador = cuenta.pagador
        tituloCuenta = cuenta.titulo
        cuentaActual = cuenta.creada
        textoOcr = ""
        totalImpreso = null
        pantalla = Pantalla.RESUMEN
    }

    fun borrarDelHistorial(creada: Long) = actualizarHistorial { Historial.borrarCuenta(it, creada) }

    private fun actualizarHistorial(cambio: (ArchivoHistorial) -> ArchivoHistorial) {
        val nuevo = cambio(historial)
        historial = nuevo
        viewModelScope.launch {
            runCatching { repositorio.guardar(nuevo) }.onFailure { error = "No se pudo guardar el historial: ${it.message}" }
        }
    }

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
