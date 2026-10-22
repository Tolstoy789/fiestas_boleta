package app.divisorcuentas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.divisorcuentas.core.DatosPago
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.Mensajes
import app.divisorcuentas.core.Precios
import app.divisorcuentas.core.Rut
import app.divisorcuentas.core.Transferencia
import java.math.BigDecimal
import kotlinx.coroutines.delay

// ---------- 4. Resumen ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PantallaResumen(vm: CuentaViewModel, volver: () -> Unit) {
    val context = LocalContext.current
    // Se recalcula solo cuando cambia algo que afecta la cuenta, no en cada tecla del nombre de la cuenta
    val reparto by remember { derivedStateOf { vm.calcular() } }
    val transferencias by remember { derivedStateOf { reparto?.let(vm::transferencias).orEmpty() } }
    var editandoDatosDe by rememberSaveable { mutableStateOf<String?>(null) }
    val r = reparto
    val enJuntada = vm.boletasJuntada.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Encabezado(
                "Cuánto paga cada uno",
                if (vm.repartido) "Repartido: el total en partes iguales." else "Detallado: cada uno paga lo que consumió.",
                volver,
                paso = pasoDe(Pantalla.RESUMEN, vm.repartido),
            )
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (r == null) {
                    Tarjeta(Modifier.fillMaxWidth(), fondo = Boleta.TimbreSuave, borde = null) {
                        Row {
                            Icon(Iconos.Alerta, contentDescription = null, Modifier.size(22.dp), tint = Boleta.Timbre)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                vm.motivoSinCalculo() ?: "No se pudo calcular la cuenta.",
                                style = MaterialTheme.typography.bodyMedium, color = Boleta.Tinta,
                            )
                        }
                    }
                    Propina(vm)
                    AjustesCuenta(vm)
                } else {
                    TicketTotal(vm, r)
                    Propina(vm)
                    AjustesCuenta(vm)

                    Seccion("Por persona", Modifier.padding(top = 10.dp))
                    r.values.forEach { TarjetaPersona(it, vm.conCentavos, conItems = !vm.repartido) }

                    Seccion("¿Quién pagó la cuenta?", Modifier.padding(top = 10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChipOpcion("Nadie aún", elegido = vm.pagador == null, alTocar = { vm.pagador = null })
                        vm.participantes.forEach { p ->
                            ChipPersona(p, elegido = vm.pagador == p, alTocar = { vm.pagador = p })
                        }
                    }
                    if (enJuntada) TarjetaJuntada(vm, r)

                    val datosPorAcreedor = vm.datosPorAcreedor(transferencias)
                    val tituloMensaje = if (enJuntada) "la juntada" else vm.tituloCuenta.ifBlank { "la cuenta" }
                    AnimatedVisibility(
                        transferencias.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Seccion("Para quedar a mano", Modifier.padding(top = 10.dp))
                            Tarjeta(Modifier.fillMaxWidth(), relleno = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                                transferencias.forEachIndexed { i, t ->
                                    if (i > 0) Separador()
                                    FilaTransferencia(t, vm.conCentavos) {
                                        // Al cobrar, la cuenta queda guardada: así la deuda aparece en el historial para marcarla pagada
                                        vm.guardarEnHistorial(r, avisar = false)
                                        enviarWhatsApp(
                                            context,
                                            Mensajes.cobro(t, tituloMensaje, datosPorAcreedor[t.a]) { dinero(it, vm.conCentavos) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (vm.pagador == null) {
                        Text(
                            "Elige quién pagó para ver quién le transfiere a quién.",
                            style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                        )
                    }
                    // Datos para depositar de cada persona que recibe plata (en una juntada pueden ser varias)
                    vm.acreedores(r).forEach { quien ->
                        TarjetaDatosPago(quien, vm.datosPagoDe(quien)) { editandoDatosDe = quien }
                    }

                    Seccion("La cuenta", Modifier.padding(top = 10.dp))
                    CampoTexto(
                        vm.tituloCuenta, { vm.tituloCuenta = it },
                        Modifier.fillMaxWidth(),
                        etiqueta = "Nombre de la cuenta (opcional)",
                        ejemplo = "Cumpleaños de la Cata",
                        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    BotonSecundario(
                        if (vm.pagador == null) "Elige quién pagó para sumar otra boleta" else "Agregar otra boleta a esta juntada",
                        { vm.agregarOtraBoleta(r) },
                        Modifier.fillMaxWidth(),
                        habilitado = vm.pagador != null,
                        icono = Iconos.Recibo,
                    )
                }
            }
        }
        if (r != null) {
            val total = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
            BarraAccion {
                BotonIcono(
                    Iconos.Guardar, "Guardar en el historial", { vm.guardarEnHistorial(r) },
                    tamano = 56.dp, fondo = Boleta.Tarjeta, borde = Boleta.Borde,
                )
                BotonSecundario(
                    "PDF",
                    {
                        vm.guardarEnHistorial(r, avisar = false)
                        vm.exportarPdf(r) { compartirPdf(context, it) }
                    },
                    habilitado = vm.ocupado == null,
                    icono = Iconos.Archivo,
                    alto = 56.dp,
                )
                BotonPrincipal(
                    "Compartir",
                    {
                        vm.guardarEnHistorial(r, avisar = false)
                        compartirTexto(
                            context,
                            resumenTexto(
                                vm.tituloCuenta, r, total, transferencias, vm.conCentavos,
                                datosPorAcreedor = vm.datosPorAcreedor(transferencias), repartido = vm.repartido,
                                boletasEnJuntada = vm.boletasJuntada.size + 1,
                            ),
                        )
                    },
                    Modifier.weight(1f),
                    icono = Iconos.Compartir,
                )
            }
        }
    }

    editandoDatosDe?.let { quien ->
        DialogoDatosPago(
            persona = quien,
            inicial = vm.datosPagoDe(quien) ?: DatosPago(titular = quien),
            alCerrar = { editandoDatosDe = null },
            alGuardar = { vm.guardarDatosPago(quien, it); editandoDatosDe = null },
        )
    }
}

/** El total como un ticket: el monto grande y el desglose con puntos, como en la boleta. */
@Composable
private fun TicketTotal(vm: CuentaViewModel, r: Map<String, Detalle>) {
    val c = vm.conCentavos
    val total = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
    val consumo = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.subtotal }
    val descuento = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.descuento }
    val impuesto = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.impuesto }
    // La propina se muestra como lo que falta para el total: así las filas siempre suman
    val propina = total - consumo + descuento - impuesto
    val forma = FormaTicket()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Boleta.Tarjeta)
            .border(1.dp, Boleta.Hilo, forma)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
    ) {
        Column {
            Text(
                "TOTAL A PAGAR",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
                color = Boleta.Grafito,
            )
            MontoAnimado(
                total, c,
                estilo = EstiloMonto.copy(fontSize = 42.sp, letterSpacing = (-1.5).sp),
                desdeCero = true,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilaMonto("Consumo", dinero(consumo, c))
                if (descuento.signum() != 0) FilaMonto("Descuento", "−" + dinero(descuento, c))
                FilaMonto("Propina ${porcentaje(vm.propinaPct)}", dinero(propina, c))
                if (impuesto.signum() != 0) FilaMonto("Impuesto ${porcentaje(vm.impuestoPct)}", dinero(impuesto, c))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PilaAvatares(vm.participantes.toList(), maximo = 5, tamano = 26.dp)
                Spacer(Modifier.width(10.dp))
                val n = r.size
                Text(
                    if (n == 1) "1 persona" else "Entre $n personas",
                    style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                )
            }
        }
    }
}

private fun porcentaje(texto: String): String =
    (Precios.porcentaje(texto) ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " %"

@Composable
private fun Propina(vm: CuentaViewModel) {
    val pct = Precios.porcentaje(vm.propinaPct)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Seccion("Propina", Modifier.padding(top = 6.dp))
        // Atajos para la propina más común en Chile (10 %) y las otras dos que más se usan
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("0", "10", "15").forEach { p ->
                ChipOpcion(
                    if (p == "0") "Sin propina" else "$p %",
                    elegido = pct?.compareTo(BigDecimal(p)) == 0,
                    alTocar = { vm.propinaPct = p },
                )
            }
            CampoMonto(
                vm.propinaPct, { vm.propinaPct = it },
                Modifier.width(88.dp),
                despues = "%",
                hayError = vm.propinaPct.isNotBlank() && pct == null,
                descripcion = "Propina en porcentaje",
            )
        }
    }
}

/** Impuesto, centavos, descuento, invitados y quién no pone propina; plegado para no estorbar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AjustesCuenta(vm: CuentaViewModel) {
    val activos = listOfNotNull(
        vm.invitados.size.takeIf { it > 0 }?.let { if (it == 1) "1 invitado" else "$it invitados" },
        "descuento".takeIf { vm.descuento.isNotBlank() },
        vm.sinPropina.size.takeIf { it > 0 }?.let { "$it sin propina" },
        "impuesto aparte".takeIf { !vm.impuestoIncluido },
        "con centavos".takeIf { vm.conCentavos },
    )
    var abierto by rememberSaveable { mutableStateOf(vm.invitados.isNotEmpty() || vm.sinPropina.isNotEmpty() || vm.descuento.isNotBlank()) }
    val giro by animateFloatAsState(if (abierto) 180f else 0f, label = "giro")
    Tarjeta(Modifier.fillMaxWidth().animateContentSize(), relleno = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (abierto) "Ocultar ajustes" else "Ver ajustes") {
                abierto = !abierto
            }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoEnCirculo(Iconos.Ajustes, tamano = 40.dp, fondo = Boleta.Carton, tinte = Boleta.Tinta)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Más ajustes", style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
                Text(
                    activos.joinToString(" · ").replaceFirstChar { it.uppercase() }
                        .ifEmpty { "Impuesto, centavos, descuento e invitados" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activos.isEmpty()) Boleta.Grafito else Boleta.Billete,
                )
            }
            Icon(Iconos.ChevronAbajo, contentDescription = null, Modifier.size(22.dp).rotate(giro), tint = Boleta.Grafito)
        }
        if (abierto) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Separador()
                FilaInterruptor(
                    "Impuesto incluido en los precios",
                    vm.impuestoIncluido,
                    { vm.impuestoIncluido = it },
                    detalle = "En Chile el IVA ya viene en el precio.",
                )
                if (!vm.impuestoIncluido) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Impuesto", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Boleta.Tinta)
                        CampoMonto(
                            vm.impuestoPct, { vm.impuestoPct = it },
                            Modifier.width(96.dp),
                            despues = "%",
                            hayError = vm.impuestoPct.isNotBlank() && Precios.porcentaje(vm.impuestoPct) == null,
                            descripcion = "Impuesto en porcentaje",
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Montos", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Boleta.Tinta)
                        Text("Redondear a pesos o con centavos", style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
                    }
                    Spacer(Modifier.width(12.dp))
                    Selector(
                        listOf("Pesos", "Centavos"),
                        if (vm.conCentavos) 1 else 0,
                        { vm.conCentavos = it == 1 },
                        Modifier.width(184.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Titulito("Descuento o cupón", "Se resta antes de la propina, en proporción a lo que consumió cada uno.")
                    CampoMonto(
                        vm.descuento, { vm.descuento = it },
                        Modifier.fillMaxWidth(),
                        antes = "$",
                        hayError = vm.descuento.isNotBlank() && Precios.parsear(vm.descuento) == null,
                        descripcion = "Descuento en pesos",
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Titulito("Invitados", "No pagan; lo suyo se reparte entre el resto.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.participantes.forEach { p ->
                            ChipPersona(p, elegido = p in vm.invitados, alTocar = { vm.alternarInvitado(p) })
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Titulito("No ponen propina", null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.participantes.forEach { p ->
                            ChipPersona(p, elegido = p in vm.sinPropina, alTocar = { vm.alternarSinPropina(p) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Titulito(titulo: String, detalle: String?) {
    Column {
        Text(titulo, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Boleta.Tinta)
        if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
    }
}

/** Lo que paga una persona; en Detallado se abre para ver qué consumió. */
@Composable
private fun TarjetaPersona(d: Detalle, centavos: Boolean, conItems: Boolean) {
    var abierto by rememberSaveable(d.nombre) { mutableStateOf(false) }
    val sePuedeAbrir = conItems && d.items.isNotEmpty()
    val giro by animateFloatAsState(if (abierto) 180f else 0f, label = "giro")
    Tarjeta(
        Modifier.fillMaxWidth().animateContentSize(),
        alTocar = if (sePuedeAbrir) ({ abierto = !abierto }) else null,
        relleno = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        etiqueta = if (abierto) "Ocultar lo que consumió" else "Ver lo que consumió",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(d.nombre, tamano = 42.dp)
            Spacer(Modifier.width(12.dp))
            // El nombre y el monto en una línea; el desglose debajo, con todo el ancho
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.nombre, Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (d.invitado) {
                        Row(
                            Modifier.clip(CircleShape).background(Boleta.Menta).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Iconos.Regalo, contentDescription = null, Modifier.size(15.dp), tint = Boleta.Billete)
                            Spacer(Modifier.width(5.dp))
                            Text("Invitado", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = Boleta.Billete)
                        }
                    } else {
                        MontoAnimado(d.total, centavos, estilo = EstiloMonto.copy(fontSize = 19.sp), desdeCero = true)
                    }
                    if (sePuedeAbrir) {
                        Icon(
                            Iconos.ChevronAbajo, contentDescription = null,
                            Modifier.padding(start = 4.dp).size(20.dp).rotate(giro), tint = Boleta.Grafito,
                        )
                    }
                }
                Text(desglose(d, centavos), style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
            }
        }
        if (abierto) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(start = 54.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.items.forEach { (nombre, monto) -> FilaMonto(nombre, dinero(monto, centavos)) }
            }
        }
    }
}

private fun desglose(d: Detalle, centavos: Boolean) = buildString {
    if (d.invitado) {
        append("Consumió ${dinero(d.subtotal, centavos)}. Lo cubre el resto.")
        return@buildString
    }
    append("Consumo ${dinero(d.subtotal, centavos)}")
    if (d.descuento.signum() != 0) append(" − descuento ${dinero(d.descuento, centavos)}")
    append(" + propina ${dinero(d.propina, centavos)}")
    if (d.impuesto.signum() != 0) append(" + impuesto ${dinero(d.impuesto, centavos)}")
    if (d.aporteInvitados.signum() != 0) append(" + invitados ${dinero(d.aporteInvitados, centavos)}")
}

/** "Benja le transfiere a Elías", el monto y el botón para cobrarle por WhatsApp. */
@Composable
private fun FilaTransferencia(t: Transferencia, centavos: Boolean, alCobrar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 58.dp, height = 36.dp)) {
            Avatar(t.de, Modifier.align(Alignment.CenterStart), tamano = 34.dp)
            Avatar(t.a, Modifier.align(Alignment.CenterEnd).border(2.dp, Boleta.Tarjeta, CircleShape), tamano = 36.dp)
        }
        Spacer(Modifier.width(12.dp))
        val monto = dinero(t.monto, centavos)
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${t.de} le transfiere $monto a ${t.a}" }) {
            Text(
                buildAnnotatedString {
                    append(t.de)
                    withStyle(SpanStyle(color = Boleta.Grafito, fontWeight = FontWeight.Normal)) { append("  →  ") }
                    append(t.a)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Boleta.Tinta,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(monto, style = EstiloMonto.copy(fontSize = 18.sp))
        }
        Spacer(Modifier.width(8.dp))
        BotonSecundario("Cobrar", alCobrar, alto = 40.dp, icono = Iconos.Chat, color = Boleta.Billete)
    }
}

@Composable
private fun TarjetaJuntada(vm: CuentaViewModel, r: Map<String, Detalle>) {
    Tarjeta(Modifier.fillMaxWidth(), fondo = Boleta.Menta, borde = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoEnCirculo(Iconos.Recibo, tamano = 40.dp, fondo = Boleta.Tarjeta, tinte = Boleta.Billete)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Juntada: ${vm.boletasJuntada.size + 1} boletas", style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
                Text(
                    (vm.boletasJuntada.map { it.titulo } + "esta").joinToString(" + ") +
                        " · total ${dinero(vm.totalJuntada(r), vm.conCentavos)}",
                    style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (vm.pagador == null) "Elige quién pagó esta boleta para calcular las transferencias de toda la juntada."
            else "Las transferencias de abajo saldan todas las boletas juntas.",
            style = MaterialTheme.typography.bodySmall,
            color = if (vm.pagador == null) Boleta.Timbre else Boleta.Tinta,
        )
        BotonTexto("Cerrar juntada", vm::cerrarJuntada, Modifier.offset(x = (-10).dp), icono = Iconos.Cerrar)
    }
}

/** Los datos bancarios de [persona], con botones para copiarlos y editarlos. */
@Composable
private fun TarjetaDatosPago(persona: String, datos: DatosPago?, alEditar: () -> Unit) {
    val portapapeles = LocalClipboardManager.current
    val haptico = LocalHapticFeedback.current
    var copiado by remember { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) {
            delay(1_800)
            copiado = false
        }
    }
    val vacio = datos == null || datos.vacio
    Tarjeta(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoEnCirculo(Iconos.Banco, tamano = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "DATOS PARA DEPOSITAR",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = Boleta.Grafito,
                )
                Text(persona, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (datos == null || vacio) {
            Text(
                "Agrégalos y saldrán en el PDF y en el mensaje. Quedan guardados para la próxima.",
                style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                datos.lineas().forEach { (etiqueta, valor) ->
                    Row {
                        Text(etiqueta, Modifier.width(116.dp), style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
                        Text(valor, style = EstiloMonto.copy(fontSize = 14.sp, color = Boleta.Tinta, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario(
                if (vacio) "Agregar datos" else "Editar",
                alEditar,
                alto = 40.dp,
                icono = if (vacio) Iconos.Mas else Iconos.Lapiz,
            )
            if (datos != null && !vacio) {
                BotonSecundario(
                    if (copiado) "Copiado" else "Copiar",
                    {
                        portapapeles.setText(AnnotatedString(datos.lineas().joinToString("\n") { (k, v) -> "$k: $v" }))
                        haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                        copiado = true
                    },
                    alto = 40.dp,
                    icono = if (copiado) Iconos.Check else Iconos.Copiar,
                    color = if (copiado) Boleta.Billete else Boleta.Tinta,
                )
            }
        }
    }
}

/** Etiqueta corta del botón -> cómo queda escrito en los datos. */
private val TIPOS_DE_CUENTA = listOf(
    "Corriente" to "Cuenta corriente",
    "Vista" to "Cuenta vista",
    "RUT" to "Cuenta RUT",
    "Ahorro" to "Cuenta de ahorro",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoDatosPago(
    persona: String,
    inicial: DatosPago,
    alCerrar: () -> Unit,
    alGuardar: (DatosPago) -> Unit,
) {
    var datos by remember { mutableStateOf(inicial) }
    val rutMalo = datos.rut.isNotBlank() && !Rut.esValido(datos.rut)
    val correoMalo = datos.correo.isNotBlank() && !datos.correo.trim().matches(Regex("""[^@\s]+@[^@\s]+\.[^@\s]+"""))
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Boleta.Tarjeta,
        title = { Text("Datos para depositar a $persona") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(
                    datos.titular, { datos = datos.copy(titular = it) }, Modifier.fillMaxWidth(),
                    etiqueta = "Nombre del titular",
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                CampoTexto(
                    datos.rut, { datos = datos.copy(rut = it) }, Modifier.fillMaxWidth(),
                    etiqueta = "RUT", ejemplo = "12.345.678-5",
                    error = "El dígito verificador no calza; revísalo.".takeIf { rutMalo },
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                CampoTexto(
                    datos.banco, { datos = datos.copy(banco = it) }, Modifier.fillMaxWidth(),
                    etiqueta = "Banco",
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                CampoTexto(datos.tipoCuenta, { datos = datos.copy(tipoCuenta = it) }, Modifier.fillMaxWidth(), etiqueta = "Tipo de cuenta")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIPOS_DE_CUENTA.forEach { (etiqueta, tipo) ->
                        ChipOpcion(
                            etiqueta,
                            elegido = datos.tipoCuenta.equals(tipo, ignoreCase = true),
                            alTocar = { datos = datos.copy(tipoCuenta = tipo) },
                        )
                    }
                }
                CampoTexto(
                    datos.numero, { datos = datos.copy(numero = it) }, Modifier.fillMaxWidth(),
                    etiqueta = "Número de cuenta",
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                CampoTexto(
                    datos.correo, { datos = datos.copy(correo = it) }, Modifier.fillMaxWidth(),
                    etiqueta = "Correo",
                    error = "Parece que al correo le falta algo.".takeIf { correoMalo },
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
        // Se puede guardar igual (el aviso es para revisar); el RUT válido queda con puntos y guion
        confirmButton = {
            TextButton(onClick = { alGuardar(datos.copy(rut = Rut.formatear(datos.rut), correo = datos.correo.trim())) }) {
                Text("Guardar", color = Boleta.Billete)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar", color = Boleta.Grafito) } },
    )
}
