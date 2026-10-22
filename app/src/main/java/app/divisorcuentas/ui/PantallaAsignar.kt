package app.divisorcuentas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

// ---------- 3. Asignar ----------

@Composable
internal fun PantallaAsignar(vm: CuentaViewModel, volver: () -> Unit) {
    // Las personas ya se eligieron al inicio: el editor queda plegado para dejar espacio a los ítems
    var editarPersonas by rememberSaveable { mutableStateOf(false) }
    val conDueno = vm.items.count { !vm.asignaciones[it.id].isNullOrEmpty() }
    val sinMarcar = vm.items.size - conDueno
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "encabezado") {
                Encabezado(
                    "¿Quién consumió qué?",
                    "Toca a quienes compartieron cada ítem. Lo que no marques se reparte entre todos.",
                    volver,
                    paso = pasoDe(Pantalla.ASIGNAR, vm.repartido),
                )
            }
            item(key = "avance") {
                Tarjeta(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$conDueno de ${vm.items.size} ítems con dueño",
                                style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta,
                            )
                            Text(
                                when (sinMarcar) {
                                    0 -> "Todo marcado."
                                    1 -> "1 sin marcar: se reparte entre todos."
                                    else -> "$sinMarcar sin marcar: se reparten entre todos."
                                },
                                style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                            )
                        }
                        PilaAvatares(vm.participantes.toList())
                    }
                    Spacer(Modifier.height(12.dp))
                    BarraProgreso(if (vm.items.isEmpty()) 0f else conDueno.toFloat() / vm.items.size)
                    Spacer(Modifier.height(6.dp))
                    BotonTexto(
                        if (editarPersonas) "Listo" else "Agregar o quitar personas",
                        { editarPersonas = !editarPersonas },
                        Modifier.offset(x = (-10).dp),
                        icono = if (editarPersonas) Iconos.Check else Iconos.Personas,
                    )
                    AnimatedVisibility(editarPersonas, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        EditorParticipantes(vm, Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(vm.items, key = { it.id }) { item ->
                TarjetaAsignacion(vm, item, Modifier.padding(horizontal = 20.dp).animateItem())
            }
        }
        BarraAccion {
            Column(Modifier.weight(1f)) {
                Text(
                    "TOTAL DE LA BOLETA",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = Boleta.Grafito,
                )
                MontoAnimado(vm.totalBoleta(), vm.conCentavos, estilo = EstiloMonto.copy(fontSize = 24.sp))
            }
            BotonPrincipal(
                "Calcular",
                { vm.pantalla = Pantalla.RESUMEN },
                habilitado = vm.participantes.isNotEmpty(),
                icono = Iconos.Siguiente,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaAsignacion(vm: CuentaViewModel, item: ItemEditable, modifier: Modifier = Modifier) {
    val elegidos = vm.asignaciones[item.id].orEmpty()
    val total = item.aItem()?.total ?: BigDecimal.ZERO
    Tarjeta(modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                buildAnnotatedString {
                    append(item.nombre.ifBlank { "Ítem" })
                    if (item.unidades > 1) {
                        withStyle(SpanStyle(color = Boleta.Grafito, fontFamily = FuenteMono, fontWeight = FontWeight.Medium, fontSize = 14.sp)) {
                            append("  ×${item.unidades}")
                        }
                    }
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta,
            )
            Spacer(Modifier.width(8.dp))
            Text(dinero(total, vm.conCentavos), style = EstiloMonto.copy(fontSize = 16.sp))
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipOpcion(
                "Todos",
                elegido = elegidos.isEmpty(),
                alTocar = { vm.repartirEntreTodos(item.id) },
                icono = if (elegidos.isEmpty()) Iconos.Check else Iconos.Personas,
                suave = true,
            )
            vm.participantes.forEach { p ->
                ChipPersona(p, elegido = p in elegidos, alTocar = { vm.alternar(item.id, p) })
            }
        }
        if (item.unidades > 1 && elegidos.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Separador()
            SelectorUnidades(vm, item, elegidos)
        }
    }
}

/** Unidades que tomó cada persona en un ítem con varias (ej. 3 cervezas). */
@Composable
private fun SelectorUnidades(vm: CuentaViewModel, item: ItemEditable, elegidos: Map<String, Int>) {
    val asignadas = elegidos.values.sum()
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        elegidos.forEach { (persona, unidades) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(persona, tamano = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(persona, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Boleta.Tinta)
                Contador(
                    unidades.toString(),
                    { nuevo -> nuevo.toIntOrNull()?.let { vm.ajustarUnidades(item.id, persona, it - unidades) } },
                    descripcion = "unidades de $persona",
                    editable = false,
                )
            }
        }
        val completo = asignadas == item.unidades
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (completo) Iconos.Check else Iconos.Repartido, contentDescription = null,
                Modifier.size(16.dp), tint = if (completo) Boleta.Billete else Boleta.Grafito,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (completo) "$asignadas de ${item.unidades} unidades asignadas"
                else "$asignadas de ${item.unidades} unidades asignadas; se reparte en esa proporción",
                style = MaterialTheme.typography.bodySmall,
                color = if (completo) Boleta.Billete else Boleta.Grafito,
            )
        }
    }
}
