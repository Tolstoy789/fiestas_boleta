package app.divisorcuentas.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.Transferencia
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOCALE = Locale.forLanguageTag("es-CL")

fun dinero(x: BigDecimal, centavos: Boolean): String =
    NumberFormat.getCurrencyInstance(LOCALE).apply {
        minimumFractionDigits = if (centavos) 2 else 0
        maximumFractionDigits = if (centavos) 2 else 0
    }.format(x)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDivisor(vm: CuentaViewModel) {
    val titulos = mapOf(
        Pantalla.BOLETA to "1. Boleta",
        Pantalla.ITEMS to "2. Ítems",
        Pantalla.ASIGNAR to "3. Quién consumió qué",
        Pantalla.RESUMEN to "4. Cuánto paga cada uno",
        Pantalla.HISTORIAL to "Historial",
    )
    val atras = when (vm.pantalla) {
        Pantalla.BOLETA -> null
        Pantalla.HISTORIAL -> Pantalla.BOLETA
        else -> Pantalla.entries[vm.pantalla.ordinal - 1]
    }
    BackHandler(enabled = atras != null) { atras?.let { vm.pantalla = it } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulos.getValue(vm.pantalla)) },
                navigationIcon = {
                    if (atras != null) TextButton(onClick = { vm.pantalla = atras }) { Text("Atrás") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            vm.error?.let {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.error = null }) { Text("OK") }
                    }
                }
            }
            vm.aviso?.let {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), color = Boleta.Billete)
                        TextButton(onClick = { vm.aviso = null }) { Text("OK") }
                    }
                }
            }
            when (vm.pantalla) {
                Pantalla.BOLETA -> PantallaBoleta(vm)
                Pantalla.ITEMS -> PantallaItems(vm)
                Pantalla.ASIGNAR -> PantallaAsignar(vm)
                Pantalla.RESUMEN -> PantallaResumen(vm)
                Pantalla.HISTORIAL -> PantallaHistorial(vm)
            }
        }
    }
}

// ---------- 1. Boleta ----------

@Composable
private fun PantallaBoleta(vm: CuentaViewModel) {
    val context = LocalContext.current
    var fotoCamara by remember { mutableStateOf<Uri?>(null) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::procesarFoto)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) fotoCamara?.let(vm::procesarFoto)
    }
    val modelo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importarModelo)
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Saca una foto a la boleta con buena luz, recta y completa.")

        val ocupado = vm.ocupado
        if (ocupado != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(ocupado)
            }
        } else {
            Button(onClick = {
                val archivo = File(context.cacheDir, "boleta.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
                fotoCamara = uri
                camara.launch(uri)
            }, Modifier.fillMaxWidth()) { Text("Tomar foto") }
            OutlinedButton(
                onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.fillMaxWidth(),
            ) { Text("Elegir de la galería") }
            TextButton(onClick = vm::empezarSinFoto) { Text("Ingresar ítems a mano") }
            val guardadas = vm.historial.cuentas.size
            if (guardadas > 0) {
                OutlinedButton(onClick = { vm.pantalla = Pantalla.HISTORIAL }, Modifier.fillMaxWidth()) {
                    Text("Historial ($guardadas)")
                }
            }
        }

        HorizontalDivider()
        Text("Modelo para ordenar los ítems", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar Gemma (AI Edge)", Modifier.weight(1f))
            Switch(checked = vm.usarGemma, onCheckedChange = vm::elegirMotor, enabled = vm.ocupado == null)
        }
        if (vm.usarGemma) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gemma mira la foto")
                    Text(
                        "Con Gemma 3n lee la boleta directo, además del texto. Más lento, pero acierta más.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = vm.gemmaLeeFoto, onCheckedChange = { vm.gemmaLeeFoto = it }, enabled = vm.ocupado == null)
            }
        }
        Text(
            vm.archivoModelo?.let { "Modelo: ${it.name}" }
                ?: "Sin modelo. Mientras tanto se usa el modo de prueba, que lee los ítems sin IA.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { modelo.launch(arrayOf("*/*")) }, enabled = vm.ocupado == null) {
            Text(if (vm.archivoModelo == null) "Importar modelo .task" else "Cambiar modelo")
        }
    }
}

// ---------- 2. Ítems ----------

@Composable
private fun PantallaItems(vm: CuentaViewModel) {
    var verTexto by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(
            "Revisa y corrige lo que leyó la app. Precio = precio unitario.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.textoOcr.isNotBlank()) {
            TextButton(onClick = { verTexto = !verTexto }) { Text(if (verTexto) "Ocultar texto leído" else "Ver texto leído") }
            if (verTexto) Text(vm.textoOcr, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.items, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        item.nombre, { vm.editarItem(item.copy(nombre = it)) },
                        Modifier.weight(1f), label = { Text("Ítem") }, singleLine = true,
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        item.cantidad, { vm.editarItem(item.copy(cantidad = it)) },
                        Modifier.width(56.dp), label = { Text("Cant.") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        item.precio, { vm.editarItem(item.copy(precio = it)) },
                        Modifier.width(100.dp), label = { Text("Precio") }, singleLine = true,
                        isError = item.aItem() == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    TextButton(onClick = { vm.borrarItem(item.id) }) { Text("✕") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total de la boleta", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(dinero(vm.totalBoleta(), vm.conCentavos), style = EstiloMonto.copy(fontSize = 20.sp))
        }
        val impreso = vm.totalImpreso
        val diferencia = vm.diferenciaConImpreso()
        if (impreso != null && diferencia != null) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "La boleta dice ${dinero(impreso, vm.conCentavos)} y los ítems suman " +
                        "${dinero(vm.totalBoleta(), vm.conCentavos)}. " +
                        if (diferencia.signum() > 0) "Puede faltar un ítem o un precio mal leído."
                        else "Puede haber un ítem repetido o un precio mal leído.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (impreso != null) {
            Text(
                "Cuadra con el total impreso en la boleta.",
                style = MaterialTheme.typography.bodySmall,
                color = Boleta.Billete,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = vm::agregarItem) { Text("Agregar ítem") }
            Button(
                onClick = { vm.pantalla = Pantalla.ASIGNAR },
                enabled = vm.items.isNotEmpty() && vm.itemsInvalidos.isEmpty(),
            ) { Text("Siguiente") }
        }
    }
}

// ---------- 3. Asignar ----------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PantallaAsignar(vm: CuentaViewModel) {
    var nuevo by rememberSaveable { mutableStateOf("") }
    var nombrandoGrupo by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(nuevo, { nuevo = it }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.agregarParticipante(nuevo); nuevo = "" }, enabled = nuevo.isNotBlank()) { Text("Agregar") }
        }
        if (vm.historial.grupos.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Grupos:", Modifier.padding(top = 14.dp, end = 4.dp), style = MaterialTheme.typography.bodySmall)
                vm.historial.grupos.forEach { g ->
                    AssistChip(onClick = { vm.usarGrupo(g) }, label = { Text("${g.nombre} (${g.miembros.size})") })
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.participantes.forEach { p ->
                InputChip(
                    selected = false,
                    onClick = { vm.borrarParticipante(p) },
                    label = { Text("$p  ✕") },
                    modifier = Modifier.semantics { contentDescription = "Quitar a $p" },
                )
            }
            if (vm.participantes.size >= 2) {
                TextButton(onClick = { nombrandoGrupo = true }) { Text("Guardar como grupo") }
            }
        }
        Text(
            "Marca quién consumió cada ítem. Si no marcas a nadie, se reparte entre todos. " +
                "En ítems con varias unidades puedes ajustar cuántas tomó cada uno.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.items, key = { it.id }) { item ->
                val elegidos = vm.asignaciones[item.id].orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        val total = item.aItem()?.total ?: BigDecimal.ZERO
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.cantidad} × ${item.nombre}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(dinero(total, vm.conCentavos), style = EstiloMonto)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            vm.participantes.forEach { p ->
                                FilterChip(selected = p in elegidos, onClick = { vm.alternar(item.id, p) }, label = { Text(p) })
                            }
                        }
                        when {
                            elegidos.isEmpty() -> Text("Entre todos", style = MaterialTheme.typography.bodySmall)
                            item.unidades > 1 -> SelectorUnidades(vm, item, elegidos)
                        }
                    }
                }
            }
        }
        Button(
            onClick = { vm.pantalla = Pantalla.RESUMEN },
            enabled = vm.participantes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("Calcular") }
    }

    if (nombrandoGrupo) {
        var nombreGrupo by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nombrandoGrupo = false },
            title = { Text("Guardar grupo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.participantes.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        nombreGrupo, { nombreGrupo = it },
                        label = { Text("Nombre del grupo") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.guardarGrupo(nombreGrupo); nombrandoGrupo = false },
                    enabled = nombreGrupo.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { nombrandoGrupo = false }) { Text("Cancelar") } },
        )
    }
}

/** Unidades que tomó cada persona en un ítem con varias (ej. 3 cervezas). */
@Composable
private fun SelectorUnidades(vm: CuentaViewModel, item: ItemEditable, elegidos: Map<String, Int>) {
    val asignadas = elegidos.values.sum()
    Column(Modifier.padding(top = 4.dp)) {
        elegidos.forEach { (persona, unidades) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(persona, Modifier.weight(1f))
                TextButton(
                    onClick = { vm.ajustarUnidades(item.id, persona, -1) },
                    enabled = unidades > 1,
                    modifier = Modifier.semantics { contentDescription = "Una unidad menos para $persona" },
                ) { Text("−", fontSize = 20.sp) }
                Text("$unidades", style = EstiloMonto.copy(color = Boleta.Tinta, fontSize = 18.sp))
                TextButton(
                    onClick = { vm.ajustarUnidades(item.id, persona, +1) },
                    modifier = Modifier.semantics { contentDescription = "Una unidad más para $persona" },
                ) { Text("+", fontSize = 20.sp) }
            }
        }
        Text(
            if (asignadas == item.unidades) "$asignadas de ${item.unidades} unidades asignadas"
            else "$asignadas de ${item.unidades} unidades asignadas; se reparte en esa proporción",
            style = MaterialTheme.typography.bodySmall,
            color = if (asignadas == item.unidades) Boleta.Billete else Boleta.Grafito,
        )
    }
}

// ---------- 4. Resumen ----------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PantallaResumen(vm: CuentaViewModel) {
    val context = LocalContext.current
    val reparto = vm.calcular()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                vm.propinaPct, { vm.propinaPct = it }, Modifier.width(120.dp),
                label = { Text("Propina %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(12.dp))
            SingleChoiceSegmentedButtonRow {
                listOf(false to "Pesos", true to "Centavos").forEachIndexed { i, (valor, texto) ->
                    SegmentedButton(
                        selected = vm.conCentavos == valor, onClick = { vm.conCentavos = valor },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(texto) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Impuesto incluido en los precios", Modifier.weight(1f))
            Switch(checked = vm.impuestoIncluido, onCheckedChange = { vm.impuestoIncluido = it })
        }
        if (!vm.impuestoIncluido) {
            OutlinedTextField(
                vm.impuestoPct, { vm.impuestoPct = it }, Modifier.width(120.dp),
                label = { Text("Impuesto %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        HorizontalDivider()

        if (reparto == null) {
            Text("Faltan participantes o hay ítems con precio inválido.")
            return@Column
        }
        reparto.values.forEach { TarjetaPersona(it, vm.conCentavos) }

        val total = reparto.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total a pagar", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(dinero(total, vm.conCentavos), style = EstiloMonto.copy(fontSize = 24.sp))
        }

        HorizontalDivider()
        Text("¿Quién pagó la cuenta?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = vm.pagador == null, onClick = { vm.pagador = null }, label = { Text("Nadie aún") })
            vm.participantes.forEach { p ->
                FilterChip(selected = vm.pagador == p, onClick = { vm.pagador = p }, label = { Text(p) })
            }
        }
        val transferencias = vm.transferencias(reparto)
        transferencias.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t.de} le transfiere a ${t.a}", Modifier.weight(1f))
                Text(dinero(t.monto, vm.conCentavos), style = EstiloMonto.copy(fontSize = 18.sp))
            }
        }

        HorizontalDivider()
        OutlinedTextField(
            vm.tituloCuenta, { vm.tituloCuenta = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre de la cuenta (opcional)") }, singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.guardarEnHistorial(reparto) }, Modifier.weight(1f)) {
                Text("Guardar")
            }
            Button(
                onClick = {
                    val texto = resumenTexto(reparto, total, transferencias, vm.conCentavos)
                    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                    context.startActivity(Intent.createChooser(envio, "Compartir cuenta"))
                },
                modifier = Modifier.weight(1f),
            ) { Text("Compartir") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Historial ----------

@Composable
private fun PantallaHistorial(vm: CuentaViewModel) {
    val fecha = remember { SimpleDateFormat("d MMM yyyy, HH:mm", LOCALE) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.historial.cuentas.isEmpty()) {
            item { Text("Aún no hay cuentas guardadas.") }
        }
        items(vm.historial.cuentas, key = { it.creada }) { c ->
            val total = c.totales.values.mapNotNull { it.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            Card(Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir cuenta") { vm.abrirDelHistorial(c) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.titulo, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${fecha.format(Date(c.creada))} · ${c.participantes.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(dinero(total, c.conCentavos), style = EstiloMonto)
                    TextButton(
                        onClick = { vm.borrarDelHistorial(c.creada) },
                        modifier = Modifier.semantics { contentDescription = "Borrar ${c.titulo}" },
                    ) { Text("✕") }
                }
            }
        }
        if (vm.historial.grupos.isNotEmpty()) {
            item {
                Text("Grupos", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
            }
            items(vm.historial.grupos, key = { "grupo-" + it.nombre }) { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.nombre, fontWeight = FontWeight.SemiBold)
                        Text(g.miembros.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { vm.borrarGrupo(g.nombre) },
                        modifier = Modifier.semantics { contentDescription = "Borrar grupo ${g.nombre}" },
                    ) { Text("✕") }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPersona(d: Detalle, centavos: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(d.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(dinero(d.total, centavos), style = EstiloMonto.copy(fontSize = 20.sp))
            }
            d.items.forEach { (nombre, monto) ->
                Text("• $nombre: ${dinero(monto, centavos)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Consumo ${dinero(d.subtotal, centavos)} + propina ${dinero(d.propina, centavos)}" +
                    if (d.impuesto.signum() != 0) " + impuesto ${dinero(d.impuesto, centavos)}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun resumenTexto(
    reparto: Map<String, Detalle>,
    total: BigDecimal,
    transferencias: List<Transferencia>,
    centavos: Boolean,
) = buildString {
    appendLine("Cuenta dividida:")
    reparto.values.forEach { appendLine("• ${it.nombre}: ${dinero(it.total, centavos)}") }
    append("Total: ${dinero(total, centavos)}")
    if (transferencias.isNotEmpty()) {
        appendLine()
        appendLine()
        appendLine("Transferencias:")
        transferencias.forEach { appendLine("• ${it.de} → ${it.a}: ${dinero(it.monto, centavos)}") }
    }
}
