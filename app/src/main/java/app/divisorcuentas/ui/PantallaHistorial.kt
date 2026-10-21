package app.divisorcuentas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.divisorcuentas.core.CuentaGuardada
import app.divisorcuentas.core.Grupo
import app.divisorcuentas.core.Mensajes
import app.divisorcuentas.core.Transferencia
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// ---------- Historial ----------

@Composable
internal fun PantallaHistorial(vm: CuentaViewModel, volver: () -> Unit) {
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportarRespaldo)
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importarRespaldo)
    }
    // Borrar no se puede deshacer: se pregunta antes
    var cuentaPorBorrar by remember { mutableStateOf<CuentaGuardada?>(null) }
    var grupoPorBorrar by remember { mutableStateOf<String?>(null) }
    val cuentas = vm.historial.cuentas
    val conDeuda = cuentas.filter { it.pendientes().isNotEmpty() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "encabezado") {
            Encabezado("Historial", "Cuentas guardadas, cobros pendientes y respaldo.", volver)
        }
        if (conDeuda.isNotEmpty()) {
            item(key = "por-cobrar") { PorCobrar(conDeuda, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
        }
        item(key = "respaldo") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (conDeuda.isEmpty()) 12.dp else 0.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(
                        "Exportar",
                        { exportar.launch("respaldo-divisor-${SimpleDateFormat("yyyy-MM-dd", LOCALE).format(Date())}.json") },
                        Modifier.weight(1f),
                        icono = Iconos.Exportar,
                    )
                    BotonSecundario(
                        "Importar",
                        { importar.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        Modifier.weight(1f),
                        icono = Iconos.Importar,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "El respaldo guarda cuentas, grupos y datos bancarios. Déjalo en Drive para pasarlo a otro teléfono.",
                    style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                )
            }
        }
        if (cuentas.isEmpty()) {
            item(key = "vacio") {
                EstadoVacio(
                    Iconos.Recibo,
                    "Aún no hay cuentas guardadas",
                    "Se guardan solas al cobrar, compartir o sacar el PDF.",
                )
            }
        } else {
            item(key = "titulo-cuentas") {
                Seccion("Cuentas · ${cuentas.size}", Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
            items(cuentas, key = { it.creada }) { c ->
                TarjetaCuenta(vm, c, alBorrar = { cuentaPorBorrar = c }, Modifier.padding(horizontal = 20.dp).animateItem())
            }
        }
        val grupos = vm.historial.grupos
        if (grupos.isNotEmpty()) {
            item(key = "titulo-grupos") {
                Seccion("Grupos · ${grupos.size}", Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
            items(grupos, key = { "grupo-" + it.nombre }) { g ->
                TarjetaGrupo(g, alBorrar = { grupoPorBorrar = g.nombre }, Modifier.padding(horizontal = 20.dp).animateItem())
            }
        }
        item(key = "fin") { Spacer(Modifier.navigationBarsPadding()) }
    }

    cuentaPorBorrar?.let { c ->
        val pendientes = c.pendientes().size
        Confirmar(
            titulo = "¿Borrar \"${c.titulo}\"?",
            detalle = when (pendientes) {
                0 -> "No se puede deshacer."
                1 -> "Todavía hay 1 transferencia por cobrar en esta cuenta."
                else -> "Todavía hay $pendientes transferencias por cobrar en esta cuenta."
            },
            alConfirmar = { vm.borrarDelHistorial(c.creada) },
            alCerrar = { cuentaPorBorrar = null },
        )
    }
    grupoPorBorrar?.let { nombre ->
        Confirmar(
            titulo = "¿Borrar el grupo \"$nombre\"?",
            detalle = "Las cuentas guardadas no cambian.",
            alConfirmar = { vm.borrarGrupo(nombre) },
            alCerrar = { grupoPorBorrar = null },
        )
    }
}

/** Lo que falta cobrar en todas las cuentas. */
@Composable
private fun PorCobrar(conDeuda: List<CuentaGuardada>, modifier: Modifier = Modifier) {
    val pendientes = conDeuda.flatMap { it.pendientes() }
    val total = pendientes.fold(BigDecimal.ZERO) { acc, t -> acc + t.monto }
    val centavos = conDeuda.any { it.conCentavos }
    Tarjeta(
        modifier.fillMaxWidth(),
        fondo = Boleta.Tinta, borde = null,
        forma = RoundedCornerShape(24.dp),
        relleno = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            "POR COBRAR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
            color = Boleta.Papel.copy(alpha = 0.6f),
        )
        MontoAnimado(total, centavos, estilo = EstiloMonto.copy(fontSize = 34.sp, color = Boleta.Corte), desdeCero = true)
        Spacer(Modifier.height(2.dp))
        Text(
            (if (pendientes.size == 1) "1 transferencia pendiente" else "${pendientes.size} transferencias pendientes") +
                if (conDeuda.size == 1) " en 1 cuenta" else " en ${conDeuda.size} cuentas",
            style = MaterialTheme.typography.bodySmall, color = Boleta.Papel.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun TarjetaCuenta(vm: CuentaViewModel, c: CuentaGuardada, alBorrar: () -> Unit, modifier: Modifier = Modifier) {
    val total = c.totales.values.mapNotNull { it.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
    val deudas = c.deudas()
    val pagaron = deudas.count { it.de in c.pagados }
    Tarjeta(modifier.fillMaxWidth().animateContentSize(), relleno = PaddingValues(0.dp)) {
        // Tocar la cabecera abre la cuenta para revisarla o cambiarla
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = "Abrir cuenta") { vm.abrirDelHistorial(c) }
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.titulo, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    fechaCorta(c.creada) + when {
                        c.parteDeJuntada -> " · parte de una juntada"
                        c.repartido -> " · repartido"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PilaAvatares(c.participantes, tamano = 26.dp)
                    Spacer(Modifier.weight(1f))
                    Text(dinero(total, c.conCentavos), style = EstiloMonto.copy(fontSize = 17.sp))
                }
            }
            BotonIcono(
                Iconos.Basura, "Borrar ${c.titulo}", alBorrar,
                Modifier.padding(start = 4.dp), tamano = 40.dp, tinte = Boleta.Grafito, borde = null,
            )
        }
        if (deudas.isNotEmpty()) {
            Separador()
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)) {
                val todos = pagaron == deudas.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (todos) "Todos pagaron" else "Pagaron $pagaron de ${deudas.size}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = if (todos) Boleta.Billete else Boleta.Tinta,
                    )
                    if (todos) Icon(Iconos.Check, contentDescription = null, Modifier.size(18.dp), tint = Boleta.Billete)
                }
                Spacer(Modifier.height(8.dp))
                BarraProgreso(pagaron.toFloat() / deudas.size)
                Spacer(Modifier.height(4.dp))
                deudas.forEach { t -> FilaDeuda(vm, c, t) }
            }
        }
    }
}

/** Una transferencia de una cuenta guardada: marcarla pagada o cobrarla. */
@Composable
private fun FilaDeuda(vm: CuentaViewModel, c: CuentaGuardada, t: Transferencia) {
    val context = LocalContext.current
    val pagado = t.de in c.pagados
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CheckRedondo(pagado, { vm.marcarPagado(c.creada, t.de, it) }, descripcion = "${t.de} pagó", Modifier.offset(x = (-8).dp))
        Text(
            "${t.de} → ${t.a}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (pagado) Boleta.Grafito else Boleta.Tinta,
            textDecoration = if (pagado) TextDecoration.LineThrough else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            dinero(t.monto, c.conCentavos),
            style = EstiloMonto.copy(fontSize = 15.sp, color = if (pagado) Boleta.Grafito else Boleta.Billete),
        )
        if (pagado) {
            Spacer(Modifier.width(12.dp))
        } else {
            BotonTexto(
                "Cobrar",
                {
                    enviarWhatsApp(
                        context,
                        Mensajes.cobro(t, c.titulo, vm.datosPagoDe(t.a)?.takeUnless { it.vacio }) { dinero(it, c.conCentavos) },
                    )
                },
                icono = Iconos.Chat,
            )
        }
    }
}

@Composable
private fun TarjetaGrupo(g: Grupo, alBorrar: () -> Unit, modifier: Modifier = Modifier) {
    Tarjeta(modifier.fillMaxWidth(), relleno = PaddingValues(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PilaAvatares(g.miembros, maximo = 3, tamano = 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.nombre, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
                Text(
                    g.miembros.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BotonIcono(Iconos.Basura, "Borrar grupo ${g.nombre}", alBorrar, tamano = 40.dp, tinte = Boleta.Grafito, borde = null)
        }
    }
}

@Composable
private fun Confirmar(titulo: String, detalle: String, alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Boleta.Tarjeta,
        icon = { Icon(Iconos.Basura, contentDescription = null, tint = Boleta.Timbre) },
        title = { Text(titulo) },
        text = { Text(detalle, color = Boleta.Grafito) },
        confirmButton = {
            TextButton(onClick = { alConfirmar(); alCerrar() }) { Text("Borrar", color = Boleta.Timbre) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar", color = Boleta.Grafito) } },
    )
}

/** "Hoy, 21:30", "Ayer, 13:05", "12 oct, 20:10" o "3 mar 2025". */
internal fun fechaCorta(cuando: Long, ahora: Long = System.currentTimeMillis()): String {
    val fecha = Calendar.getInstance().apply { timeInMillis = cuando }
    val hoy = Calendar.getInstance().apply { timeInMillis = ahora }
    val ayer = (hoy.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun mismoDia(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val hora = SimpleDateFormat("HH:mm", LOCALE).format(Date(cuando))
    return when {
        mismoDia(fecha, hoy) -> "Hoy, $hora"
        mismoDia(fecha, ayer) -> "Ayer, $hora"
        fecha.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) -> SimpleDateFormat("d MMM, HH:mm", LOCALE).format(Date(cuando))
        else -> SimpleDateFormat("d MMM yyyy", LOCALE).format(Date(cuando))
    }
}
