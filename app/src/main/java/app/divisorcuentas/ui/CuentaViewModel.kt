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
import app.divisorcuentas.core.Ajustes
import app.divisorcuentas.core.ArchivoHistorial
import app.divisorcuentas.core.BoletaJuntada
import app.divisorcuentas.core.CuentaGuardada
import app.divisorcuentas.core.DatosPago
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.DivisorCuenta
import app.divisorcuentas.core.ExtraccionItems
import app.divisorcuentas.core.Grupo
import app.divisorcuentas.core.Historial
import app.divisorcuentas.core.Item
import app.divisorcuentas.core.ItemGuardado
import app.divisorcuentas.core.Juntada
import app.divisorcuentas.core.Precios
import app.divisorcuentas.core.Saldos
import app.divisorcuentas.core.Transferencia
import app.divisorcuentas.core.TransferenciaGuardada
import app.divisorcuentas.historial.RepositorioHistorial
import app.divisorcuentas.llm.ModeloLocal
import app.divisorcuentas.llm.MotorAiEdge
import app.divisorcuentas.llm.MotorLlm
import app.divisorcuentas.llm.MotorMock
import app.divisorcuentas.llm.extraerItems
import app.divisorcuentas.ocr.OcrBoleta
import app.divisorcuentas.ocr.cargarFotoReducida
import app.divisorcuentas.pdf.DatosPdf
import app.divisorcuentas.pdf.DetallePdf
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Pantalla { INICIO, BOLETA, ITEMS, ASIGNAR, RESUMEN, HISTORIAL }

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

    var pantalla by mutableStateOf(Pantalla.INICIO)
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
    /**
     * Modo de cálculo, elegido al inicio: true = "Repartido" (el total en partes
     * iguales), false = "Detallado" (cada uno paga lo que consumió).
     */
    var repartido by mutableStateOf(false)
    /** No pagan; lo suyo se reparte entre el resto (ej. el cumpleañero). */
    val invitados = mutableStateListOf<String>()
    /** No ponen propina. */
    val sinPropina = mutableStateListOf<String>()
    /** Monto a restar de la boleta (cupón, promoción). */
    var descuento by mutableStateOf("")
    /** Boletas anteriores de la misma juntada (ej. el bar antes de la comida). */
    val boletasJuntada = mutableStateListOf<BoletaJuntada>()
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
        invitados.remove(nombre); sinPropina.remove(nombre)
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
                validos, partesParaCalcular(), participantes.toList(),
                propinaPct = Precios.parsear(propinaPct) ?: BigDecimal.ZERO,
                impuestoPct = Precios.parsear(impuestoPct) ?: BigDecimal.ZERO,
                impuestoIncluido = impuestoIncluido,
                unidad = if (conCentavos) BigDecimal("0.01") else BigDecimal.ONE,
                ajustes = Ajustes(
                    invitados = invitados.filter { it in participantes }.toSet(),
                    sinPropina = sinPropina.filter { it in participantes }.toSet(),
                    descuento = Precios.parsear(descuento) ?: BigDecimal.ZERO,
                ),
            )
        }.onFailure { error = it.message }.getOrNull()
    }

    /** Quién le transfiere a quién: de esta boleta, o el saldo neto de toda la juntada. */
    fun transferencias(reparto: Map<String, Detalle>): List<Transferencia> {
        if (boletasJuntada.isEmpty()) {
            val quien = pagador ?: return emptyList()
            return Saldos.pagoUnico(reparto.mapValues { it.value.total }, quien)
        }
        return Juntada.transferencias(boletasJuntada + boletaActual(reparto))
    }

    private fun boletaActual(reparto: Map<String, Detalle>) =
        BoletaJuntada(tituloOPorDefecto(System.currentTimeMillis()), reparto.mapValues { it.value.total }, pagador)

    private fun tituloOPorDefecto(cuando: Long) = tituloCuenta.trim().ifEmpty {
        "Cuenta del " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("es-CL")).format(Date(cuando))
    }

    /** Total de la juntada, sumando las boletas anteriores y la actual. */
    fun totalJuntada(reparto: Map<String, Detalle>): BigDecimal =
        (boletasJuntada + boletaActual(reparto)).flatMap { it.totales.values }.fold(BigDecimal.ZERO, BigDecimal::add)

    /**
     * Guarda esta boleta y empieza otra de la misma juntada, con las mismas
     * personas. Las transferencias se calculan al final sobre todas.
     */
    fun agregarOtraBoleta(reparto: Map<String, Detalle>) {
        guardarEnHistorial(reparto, parteDeJuntada = true)
        boletasJuntada.add(boletaActual(reparto))
        items.clear(); asignaciones.clear()
        textoOcr = ""; totalImpreso = null
        descuento = ""
        pagador = null
        cuentaActual = null
        tituloCuenta = ""
        pantalla = Pantalla.BOLETA
        aviso = "Boleta ${boletasJuntada.size} guardada. Saca la foto de la siguiente."
    }

    fun cerrarJuntada() {
        boletasJuntada.clear()
        aviso = "Juntada cerrada. La próxima boleta parte de cero."
    }

    fun alternarInvitado(persona: String) {
        if (!invitados.remove(persona)) invitados.add(persona)
    }

    fun alternarSinPropina(persona: String) {
        if (!sinPropina.remove(persona)) sinPropina.add(persona)
    }

    /** Arma el PDF con el detalle de la cuenta y se lo entrega a [alTerminar] para compartirlo. */
    fun exportarPdf(reparto: Map<String, Detalle>, alTerminar: (File) -> Unit) = trabajar("Armando el PDF…") {
        val ahora = System.currentTimeMillis()
        val datos = DatosPdf(
            titulo = tituloCuenta.trim().ifEmpty {
                "Cuenta del " + SimpleDateFormat("d MMM", Locale.forLanguageTag("es-CL")).format(Date(ahora))
            },
            fecha = ahora,
            items = items.mapNotNull { it.aItem() },
            partes = partesParaCalcular(),
            participantes = participantes.toList(),
            reparto = reparto,
            repartido = repartido,
            datosPorAcreedor = transferencias(reparto).map { it.a }.distinct()
                .mapNotNull { a -> datosPagoDe(a)?.takeUnless { it.vacio }?.let { a to it } }.toMap(),
            boletasEnJuntada = boletasJuntada.size + 1,
            propinaPct = Precios.parsear(propinaPct) ?: BigDecimal.ZERO,
            impuestoPct = if (impuestoIncluido) null else Precios.parsear(impuestoPct) ?: BigDecimal.ZERO,
            pagador = pagador,
            transferencias = transferencias(reparto),
            centavos = conCentavos,
        )
        alTerminar(DetallePdf.crear(getApplication(), datos))
    }

    fun totalBoleta(): BigDecimal = DivisorCuenta.totalBoleta(items.mapNotNull { it.aItem() })

    private fun partesPorIndice(): Map<Int, Map<String, Int>> =
        items.mapIndexed { i, it -> i to asignaciones[it.id].orEmpty() }.toMap()

    /** En modo Repartido no se mira quién consumió qué: todo entre todos (se conservan las marcas por si se cambia). */
    private fun partesParaCalcular(): Map<Int, Map<String, Int>> = if (repartido) emptyMap() else partesPorIndice()

    /** Pantalla que sigue a la de ítems: en Repartido no hace falta asignar. */
    fun despuesDeItems() {
        pantalla = if (repartido) Pantalla.RESUMEN else Pantalla.ASIGNAR
    }

    // ---- Datos para depositar ----

    fun datosPagoDe(persona: String): DatosPago? = Historial.datosPagoDe(historial, persona)

    fun guardarDatosPago(persona: String, datos: DatosPago) {
        actualizarHistorial { Historial.guardarDatosPago(it, persona, datos) }
        aviso = if (datos.vacio) "Datos de $persona borrados." else "Datos para transferir a $persona guardados."
    }

    // ---- Historial ----

    fun guardarEnHistorial(reparto: Map<String, Detalle>, parteDeJuntada: Boolean = false) {
        val creada = cuentaActual ?: System.currentTimeMillis()
        val titulo = tituloOPorDefecto(creada)
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
            repartido = repartido,
            invitados = invitados.filter { it in participantes },
            sinPropina = sinPropina.filter { it in participantes },
            descuento = descuento.ifBlank { "0" },
            transferencias = transferencias(reparto).map { TransferenciaGuardada(it.de, it.a, it.monto.toPlainString()) },
            pagados = historial.cuentas.firstOrNull { it.creada == creada }?.pagados.orEmpty(),
            parteDeJuntada = parteDeJuntada,
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
        repartido = cuenta.repartido
        invitados.clear(); invitados.addAll(cuenta.invitados)
        sinPropina.clear(); sinPropina.addAll(cuenta.sinPropina)
        descuento = cuenta.descuento.takeUnless { it == "0" }.orEmpty()
        boletasJuntada.clear()
        pagador = cuenta.pagador
        tituloCuenta = cuenta.titulo
        cuentaActual = cuenta.creada
        textoOcr = ""
        totalImpreso = null
        pantalla = Pantalla.RESUMEN
    }

    fun marcarPagado(creada: Long, persona: String, pagado: Boolean) =
        actualizarHistorial { Historial.marcarPagado(it, creada, persona, pagado) }

    // ---- Respaldo ----

    /** Escribe el historial completo (cuentas, grupos y datos bancarios) en [uri]. */
    fun exportarRespaldo(uri: Uri) = trabajar("Guardando el respaldo…") {
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(Historial.aJson(historial).toByteArray())
            }
        }
        aviso = "Respaldo guardado: ${historial.cuentas.size} cuentas y ${historial.grupos.size} grupos."
    }

    /** Lee un respaldo y lo junta con lo que ya hay, sin borrar nada. */
    fun importarRespaldo(uri: Uri) = trabajar("Leyendo el respaldo…") {
        val texto = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }
        val respaldo = Historial.desdeJson(texto)
        if (respaldo == ArchivoHistorial()) throw IllegalStateException("Ese archivo no es un respaldo de la app o está vacío.")
        actualizarHistorial { Historial.combinar(it, respaldo) }
        aviso = "Respaldo importado: ahora hay ${historial.cuentas.size} cuentas y ${historial.grupos.size} grupos."
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
