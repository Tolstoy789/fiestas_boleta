package app.divisorcuentas.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.divisorcuentas.core.Ajustes
import app.divisorcuentas.core.ArchivoHistorial
import app.divisorcuentas.core.BoletaJuntada
import app.divisorcuentas.core.Borrador
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
import app.divisorcuentas.core.boleta
import app.divisorcuentas.core.guardada
import app.divisorcuentas.historial.RepositorioHistorial
import app.divisorcuentas.llm.ModeloLocal
import app.divisorcuentas.llm.MotorAiEdge
import app.divisorcuentas.llm.MotorLlm
import app.divisorcuentas.llm.MotorMock
import app.divisorcuentas.llm.extraerItems
import app.divisorcuentas.ocr.OcrBoleta
import app.divisorcuentas.ocr.cargarFotoReducida
import app.divisorcuentas.ocr.pdfAImagen
import app.divisorcuentas.pdf.DatosPdf
import app.divisorcuentas.pdf.DetallePdf
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/** Un ítem o una persona recién borrados, con lo necesario para devolverlos. */
class Borrado(val mensaje: String, val deshacer: () -> Unit)

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
    /** Lo último que se borró, mientras se ofrece deshacerlo. */
    var borrado by mutableStateOf<Borrado?>(null)
        private set
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

    /** Lectura del historial al abrir la app; nada se guarda antes de que termine, para no pisarlo. */
    private val carga: Job = viewModelScope.launch { historial = repositorio.leer() }

    init {
        repositorio.leerBorrador()?.let(::restaurar)
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

    /** Cargar Gemma (varios GB) toma segundos: se hace fuera del hilo de la pantalla para que no se congele. */
    private suspend fun motor(): MotorLlm {
        if (!usarGemma) return MotorMock()
        motorGemma?.let { return it }
        ocupado = "Cargando Gemma (la primera vez tarda un poco)…"
        val archivo = requireNotNull(archivoModelo)
        return withContext(Dispatchers.Default) { MotorAiEdge(getApplication(), archivo) }.also { motorGemma = it }
    }

    // ---- Boleta ----

    fun procesarFoto(uri: Uri) = trabajar("Leyendo la boleta…") { leerBoleta(uri) }

    /**
     * Una boleta que llegó después del evento (por correo, WhatsApp o Drive): una imagen
     * o un PDF. Del PDF se lee la primera página.
     */
    fun procesarArchivo(uri: Uri) {
        val app = getApplication<Application>()
        if (app.contentResolver.getType(uri) != "application/pdf") return procesarFoto(uri)
        trabajar("Abriendo el PDF…") {
            val imagen = pdfAImagen(app, uri)
            ocupado = "Leyendo la boleta…"
            leerBoleta(FileProvider.getUriForFile(app, "${app.packageName}.archivos", imagen))
        }
    }

    private suspend fun leerBoleta(uri: Uri) {
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
        val extraccion = extraerItems(motor, textoOcr, foto)
        nuevaCuenta()
        extraccion.items.forEach { items.add(it.editable()) }
        when {
            extraccion.items.isEmpty() -> error = "No se reconocieron ítems; agrégalos a mano."
            usarGemma && !extraccion.delModelo ->
                aviso = "Gemma no entregó los ítems; se leyeron directo del texto. Revísalos."
        }
        pantalla = Pantalla.ITEMS
    }

    fun empezarSinFoto() {
        textoOcr = ""
        totalImpreso = null
        nuevaCuenta()
        pantalla = Pantalla.ITEMS
    }

    /** Limpia los ítems, asignaciones y el descuento; los participantes se mantienen para la próxima ronda. */
    private fun nuevaCuenta() {
        items.clear(); asignaciones.clear()
        descuento = ""
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
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = items.removeAt(i)
        val partes = asignaciones.remove(id)
        borrado = Borrado("Borraste ${item.nombre.ifBlank { "un ítem" }}") {
            items.add(i.coerceAtMost(items.size), item)
            partes?.let { asignaciones[id] = it }
        }
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
        if (n.isEmpty()) return
        if (participantes.any { it.equals(n, ignoreCase = true) }) aviso = "$n ya está en la lista."
        else participantes.add(n)
    }

    fun borrarParticipante(nombre: String) {
        val i = participantes.indexOf(nombre)
        if (i < 0) return
        // Lo necesario para devolverla tal como estaba: sus marcas, si era invitada, si pagó
        val eraInvitado = nombre in invitados
        val sinPonerPropina = nombre in sinPropina
        val pago = pagador == nombre
        val unidades = asignaciones.mapNotNull { (id, partes) -> partes[nombre]?.let { id to it } }
        participantes.removeAt(i)
        invitados.remove(nombre); sinPropina.remove(nombre)
        asignaciones.keys.toList().forEach { id -> asignaciones[id] = asignaciones.getValue(id) - nombre }
        if (pago) pagador = null
        borrado = Borrado("Quitaste a $nombre") {
            if (participantes.any { it.equals(nombre, ignoreCase = true) }) return@Borrado
            participantes.add(i.coerceAtMost(participantes.size), nombre)
            if (eraInvitado) invitados.add(nombre)
            if (sinPonerPropina) sinPropina.add(nombre)
            if (pago && pagador == null) pagador = nombre
            unidades.forEach { (id, u) -> asignaciones[id]?.let { asignaciones[id] = it + (nombre to u) } }
        }
    }

    /** Deshace el último borrado (un ítem o una persona). */
    fun deshacerBorrado() {
        borrado?.deshacer?.invoke()
        borrado = null
    }

    fun olvidarBorrado() {
        borrado = null
    }

    fun usarGrupo(grupo: Grupo) = grupo.miembros
        .filter { m -> participantes.none { it.equals(m, ignoreCase = true) } }
        .forEach { participantes.add(it) }

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

    /** Vuelve el ítem a "entre todos": nadie marcado. */
    fun repartirEntreTodos(itemId: Long) {
        asignaciones.remove(itemId)
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

    /** Por qué todavía no se puede calcular, en palabras; null si se puede. */
    fun motivoSinCalculo(): String? = when {
        participantes.isEmpty() -> "Agrega al menos una persona."
        items.isEmpty() -> "Agrega al menos un ítem."
        itemsInvalidos.isNotEmpty() ->
            "Revisa el precio de: " + itemsInvalidos.joinToString(", ") { it.nombre.ifBlank { "ítem sin nombre" } } + "."
        participantes.all { it in invitados } -> "Alguien tiene que pagar: no pueden ser todos invitados."
        else -> null
    }

    /** El reparto de esta boleta, o null si falta algo (ver [motivoSinCalculo]). No cambia ningún estado. */
    fun calcular(): Map<String, Detalle>? {
        if (motivoSinCalculo() != null) return null
        val validos = items.map { it.aItem()!! }
        return runCatching {
            DivisorCuenta.dividirPorPartes(
                validos, partesParaCalcular(), participantes.toList(),
                propinaPct = Precios.porcentaje(propinaPct) ?: BigDecimal.ZERO,
                impuestoPct = Precios.porcentaje(impuestoPct) ?: BigDecimal.ZERO,
                impuestoIncluido = impuestoIncluido,
                unidad = if (conCentavos) BigDecimal("0.01") else BigDecimal.ONE,
                ajustes = Ajustes(
                    invitados = invitados.filter { it in participantes }.toSet(),
                    sinPropina = sinPropina.filter { it in participantes }.toSet(),
                    descuento = Precios.parsear(descuento) ?: BigDecimal.ZERO,
                ),
            )
        }.getOrNull()
    }

    /**
     * Quién le transfiere a quién: de esta boleta, o el saldo neto de toda la juntada.
     * Sin saber quién pagó esta boleta no hay transferencias (en una juntada no cuadrarían).
     */
    fun transferencias(reparto: Map<String, Detalle>): List<Transferencia> {
        val quien = pagador ?: return emptyList()
        if (boletasJuntada.isEmpty()) return Saldos.pagoUnico(reparto.mapValues { it.value.total }, quien)
        return Juntada.transferencias(boletasJuntada + boletaActual(reparto))
    }

    /** Quienes reciben plata: a ellos se les piden los datos para depositar. */
    fun acreedores(reparto: Map<String, Detalle>): List<String> =
        transferencias(reparto).map { it.a }.distinct().ifEmpty { listOfNotNull(pagador) }

    /** Datos bancarios de quienes reciben transferencias, los que estén guardados. */
    fun datosPorAcreedor(transferencias: List<Transferencia>): Map<String, DatosPago> =
        transferencias.map { it.a }.distinct()
            .mapNotNull { a -> datosPagoDe(a)?.takeUnless { it.vacio }?.let { a to it } }.toMap()

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
        if (pagador == null) {
            aviso = "Primero elige quién pagó esta boleta."
            return
        }
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
        if (invitados.remove(persona)) return
        if (participantes.none { it != persona && it !in invitados }) {
            aviso = "Alguien tiene que pagar: no pueden ser todos invitados."
            return
        }
        invitados.add(persona)
    }

    fun alternarSinPropina(persona: String) {
        if (!sinPropina.remove(persona)) sinPropina.add(persona)
    }

    /** Arma el PDF con el detalle de la cuenta y se lo entrega a [alTerminar] para compartirlo. */
    fun exportarPdf(reparto: Map<String, Detalle>, alTerminar: (File) -> Unit) = trabajar("Armando el PDF…") {
        val ahora = System.currentTimeMillis()
        val deudas = transferencias(reparto)
        val datos = DatosPdf(
            titulo = tituloOPorDefecto(ahora),
            fecha = ahora,
            items = items.mapNotNull { it.aItem() },
            partes = partesParaCalcular(),
            participantes = participantes.toList(),
            reparto = reparto,
            repartido = repartido,
            datosPorAcreedor = datosPorAcreedor(deudas),
            juntada = if (boletasJuntada.isEmpty()) emptyList() else boletasJuntada + boletaActual(reparto),
            propinaPct = Precios.porcentaje(propinaPct) ?: BigDecimal.ZERO,
            impuestoPct = if (impuestoIncluido) null else Precios.porcentaje(impuestoPct) ?: BigDecimal.ZERO,
            pagador = pagador,
            transferencias = deudas,
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

    /**
     * Guarda la cuenta (o la reemplaza, si ya estaba). Con [avisar] = false se guarda
     * sin mostrar nada: así pasa al cobrar o compartir, para que la deuda quede anotada.
     */
    fun guardarEnHistorial(reparto: Map<String, Detalle>, parteDeJuntada: Boolean = false, avisar: Boolean = true) {
        val creada = cuentaActual ?: System.currentTimeMillis()
        val titulo = tituloOPorDefecto(creada)
        val anterior = historial.cuentas.firstOrNull { it.creada == creada }
        // Una boleta intermedia de juntada sigue siéndolo aunque se abra y se guarde de nuevo
        val cuenta = comoGuardada(reparto, creada, titulo, parteDeJuntada || anterior?.parteDeJuntada == true)
            .copy(pagados = anterior?.pagados.orEmpty())
        cuentaActual = creada
        tituloCuenta = titulo
        actualizarHistorial { Historial.agregarCuenta(it, cuenta) }
        if (avisar) aviso = "Guardada en el historial."
    }

    /** La cuenta en pantalla en el formato del historial; sin [reparto] van sin totales ni transferencias. */
    private fun comoGuardada(reparto: Map<String, Detalle>?, creada: Long, titulo: String, parteDeJuntada: Boolean) =
        CuentaGuardada(
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
            totales = reparto?.mapValues { it.value.total.toPlainString() }.orEmpty(),
            repartido = repartido,
            invitados = invitados.filter { it in participantes },
            sinPropina = sinPropina.filter { it in participantes },
            descuento = descuento.ifBlank { "0" },
            transferencias = reparto?.let { transferencias(it) }.orEmpty()
                .map { TransferenciaGuardada(it.de, it.a, it.monto.toPlainString()) },
            parteDeJuntada = parteDeJuntada,
        )

    fun abrirDelHistorial(cuenta: CuentaGuardada) {
        cargarCuenta(cuenta)
        boletasJuntada.clear()
        cuentaActual = cuenta.creada
        textoOcr = ""
        totalImpreso = null
        pantalla = Pantalla.RESUMEN
    }

    /** Pone en pantalla los datos de [cuenta]: personas, ítems, marcas y ajustes. */
    private fun cargarCuenta(cuenta: CuentaGuardada) {
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
        pagador = cuenta.pagador
        tituloCuenta = cuenta.titulo
    }

    // ---- Cuenta en curso (si Android cierra la app) ----

    /** Se llama cuando la app pasa a segundo plano. */
    fun guardarBorrador() {
        val ahora = System.currentTimeMillis()
        val b = Borrador(
            guardado = ahora,
            pantalla = pantalla.name,
            cuenta = comoGuardada(calcular(), cuentaActual ?: ahora, tituloCuenta, parteDeJuntada = false),
            cuentaActual = cuentaActual,
            juntada = boletasJuntada.map { it.guardada() },
            textoOcr = textoOcr,
            totalImpreso = totalImpreso?.toPlainString(),
        )
        runCatching { repositorio.guardarBorrador(b) }
    }

    /**
     * Sigue donde se quedó. Si pasaron más de unas horas solo recupera las personas
     * y el modo, para que la próxima salida no parta con la cuenta vieja.
     */
    private fun restaurar(b: Borrador) {
        if (!b.vigente(System.currentTimeMillis())) {
            participantes.addAll(b.cuenta.participantes)
            repartido = b.cuenta.repartido
            return
        }
        cargarCuenta(b.cuenta)
        cuentaActual = b.cuentaActual
        boletasJuntada.addAll(b.juntada.map { it.boleta() })
        textoOcr = b.textoOcr
        totalImpreso = b.totalImpreso?.toBigDecimalOrNull()
        pantalla = Pantalla.entries.firstOrNull { it.name == b.pantalla } ?: Pantalla.INICIO
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
        if (!carga.isCompleted) {
            viewModelScope.launch { carga.join(); actualizarHistorial(cambio) }
            return
        }
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
