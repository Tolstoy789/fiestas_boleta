package app.divisorcuentas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------- 2. Ítems ----------

@Composable
internal fun PantallaItems(vm: CuentaViewModel, volver: () -> Unit) {
    var verTexto by rememberSaveable { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val alcance = rememberCoroutineScope()
    // El ítem recién agregado recibe el foco para escribir su nombre de inmediato
    var enfocar by remember { mutableStateOf<Long?>(null) }
    val agregar = {
        vm.agregarItem()
        enfocar = vm.items.last().id
        alcance.launch { lista.animateScrollToItem(PRIMER_ITEM + vm.items.lastIndex) }
        Unit
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            state = lista,
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "encabezado") {
                Encabezado(
                    "Revisa los ítems",
                    "Corrige lo que leyó la app. El precio es por unidad.",
                    volver,
                    paso = pasoDe(Pantalla.ITEMS, vm.repartido),
                )
            }
            item(key = "revision") { Revision(vm, verTexto) { verTexto = !verTexto } }
            items(vm.items, key = { it.id }) { item ->
                TarjetaItem(vm, item, enfocar = enfocar == item.id, Modifier.padding(horizontal = 20.dp).animateItem())
            }
            item(key = "agregar") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (vm.items.isEmpty()) {
                        EstadoVacio(Iconos.Recibo, "Todavía no hay ítems", "Agrega lo que se pidió, con su precio por unidad.")
                    }
                    AgregarItem(agregar)
                }
            }
        }
        BarraAccion {
            Column(Modifier.weight(1f)) {
                Text(
                    when (val n = vm.items.size) {
                        0 -> "SIN ÍTEMS"
                        1 -> "1 ÍTEM"
                        else -> "$n ÍTEMS"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = Boleta.Grafito,
                )
                MontoAnimado(vm.totalBoleta(), vm.conCentavos, estilo = EstiloMonto.copy(fontSize = 24.sp))
            }
            BotonPrincipal(
                if (vm.repartido) "Calcular" else "Siguiente",
                vm::despuesDeItems,
                habilitado = vm.items.isNotEmpty() && vm.itemsInvalidos.isEmpty(),
                icono = Iconos.Siguiente,
            )
        }
    }
}

/** Posición del primer ítem en la lista (después del encabezado y la revisión). */
private const val PRIMER_ITEM = 2

/** Si los ítems cuadran con el total impreso, y el texto que leyó la app. */
@Composable
private fun Revision(vm: CuentaViewModel, verTexto: Boolean, alternarTexto: () -> Unit) {
    val impreso = vm.totalImpreso
    val diferencia = vm.diferenciaConImpreso()
    if (impreso == null && vm.textoOcr.isBlank()) return
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (impreso != null && diferencia != null) {
            Tarjeta(Modifier.fillMaxWidth(), fondo = Boleta.AmbarSuave, borde = null, relleno = PaddingValues(14.dp)) {
                Row {
                    Icon(Iconos.Alerta, contentDescription = null, Modifier.size(22.dp), tint = Boleta.Ambar)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "La boleta dice ${dinero(impreso, vm.conCentavos)} y los ítems suman " +
                            "${dinero(vm.totalBoleta(), vm.conCentavos)}. " +
                            if (diferencia.signum() > 0) "Puede faltar un ítem o un precio mal leído."
                            else "Puede haber un ítem repetido o un precio mal leído.",
                        style = MaterialTheme.typography.bodyMedium, color = Boleta.Tinta,
                    )
                }
            }
        } else if (impreso != null) {
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Boleta.Menta).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Iconos.Check, contentDescription = null, Modifier.size(20.dp), tint = Boleta.Billete)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cuadra con el total de la boleta: ${dinero(impreso, vm.conCentavos)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Boleta.Billete,
                )
            }
        }
        if (vm.textoOcr.isNotBlank()) {
            BotonTexto(
                if (verTexto) "Ocultar el texto leído" else "Ver el texto leído",
                alternarTexto,
                Modifier.offset(x = (-10).dp),
                icono = Iconos.Recibo,
                color = Boleta.Grafito,
            )
            AnimatedVisibility(verTexto, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Tarjeta(Modifier.fillMaxWidth(), relleno = PaddingValues(14.dp)) {
                    Text(vm.textoOcr, style = MaterialTheme.typography.labelMedium.copy(lineHeight = 18.sp), color = Boleta.Grafito)
                }
            }
        }
    }
}

@Composable
private fun TarjetaItem(vm: CuentaViewModel, item: ItemEditable, enfocar: Boolean, modifier: Modifier = Modifier) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(enfocar) { if (enfocar) foco.requestFocus() }
    val leido = item.aItem()
    Tarjeta(modifier.fillMaxWidth(), relleno = PaddingValues(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val estilo = MaterialTheme.typography.titleMedium.copy(color = Boleta.Tinta)
            BasicTextField(
                item.nombre, { vm.editarItem(item.copy(nombre = it)) },
                Modifier.weight(1f).focusRequester(foco).padding(vertical = 12.dp),
                textStyle = estilo,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                cursorBrush = SolidColor(Boleta.Tinta),
                decorationBox = { campo ->
                    Box {
                        if (item.nombre.isEmpty()) Text("Nombre del ítem", style = estilo.copy(color = Boleta.Grafito.copy(alpha = 0.55f)))
                        campo()
                    }
                },
            )
            BotonIcono(
                Iconos.Basura, "Borrar ${item.nombre.ifBlank { "ítem" }}", { vm.borrarItem(item.id) },
                tamano = 40.dp, tinte = Boleta.Grafito, borde = null,
            )
        }
        Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Contador(item.cantidad, { vm.editarItem(item.copy(cantidad = it)) }, descripcion = item.nombre.ifBlank { "ítem" })
            Text("×", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge, color = Boleta.Grafito)
            CampoMonto(
                item.precio, { vm.editarItem(item.copy(precio = it)) },
                Modifier.width(112.dp),
                antes = "$",
                hayError = leido == null,
                descripcion = "Precio por unidad de ${item.nombre.ifBlank { "ítem" }}",
            )
            Spacer(Modifier.weight(1f))
            Text(
                leido?.let { dinero(it.total, vm.conCentavos) } ?: "—",
                style = EstiloMonto.copy(fontSize = 16.sp, color = if (leido == null) Boleta.Grafito else Boleta.Billete),
                maxLines = 1,
            )
        }
        if (leido == null) {
            Text(
                "Escribe el precio por unidad, por ejemplo 4.900.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = Boleta.Timbre,
            )
        }
    }
}

/** Botón de rayitas al final de la lista. */
@Composable
private fun AgregarItem(alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .bordePunteado(Boleta.Borde, 20.dp)
            .clickable(role = Role.Button, onClick = alTocar),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Mas, contentDescription = null, Modifier.size(20.dp), tint = Boleta.Tinta)
        Spacer(Modifier.width(8.dp))
        Text("Agregar ítem", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Boleta.Tinta)
    }
}
