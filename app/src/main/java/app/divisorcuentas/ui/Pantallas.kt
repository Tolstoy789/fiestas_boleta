package app.divisorcuentas.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.divisorcuentas.core.Detalle
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
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
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulos.getValue(vm.pantalla)) },
                navigationIcon = {
                    if (vm.pantalla != Pantalla.BOLETA) {
                        TextButton(onClick = { vm.pantalla = Pantalla.entries[vm.pantalla.ordinal - 1] }) { Text("Atrás") }
                    }
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
            when (vm.pantalla) {
                Pantalla.BOLETA -> PantallaBoleta(vm)
                Pantalla.ITEMS -> PantallaItems(vm)
                Pantalla.ASIGNAR -> PantallaAsignar(vm)
                Pantalla.RESUMEN -> PantallaResumen(vm)
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
        }

        HorizontalDivider()
        Text("Modelo para ordenar los ítems", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar Gemma (AI Edge)", Modifier.weight(1f))
            Switch(checked = vm.usarGemma, onCheckedChange = vm::elegirMotor, enabled = vm.ocupado == null)
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
        Text("Total de la boleta: ${dinero(vm.totalBoleta(), vm.conCentavos)}", fontWeight = FontWeight.Bold)
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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(nuevo, { nuevo = it }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.agregarParticipante(nuevo); nuevo = "" }, enabled = nuevo.isNotBlank()) { Text("Agregar") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.participantes.forEach { p ->
                InputChip(selected = false, onClick = { vm.borrarParticipante(p) }, label = { Text("$p  ✕") })
            }
        }
        Text(
            "Marca quién consumió cada ítem. Si no marcas a nadie, se reparte entre todos.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.items, key = { it.id }) { item ->
                val elegidos = vm.asignaciones[item.id].orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        val total = item.aItem()?.total ?: BigDecimal.ZERO
                        Text("${item.cantidad} × ${item.nombre}  ·  ${dinero(total, vm.conCentavos)}", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            vm.participantes.forEach { p ->
                                FilterChip(selected = p in elegidos, onClick = { vm.alternar(item.id, p) }, label = { Text(p) })
                            }
                        }
                        if (elegidos.isEmpty()) Text("Entre todos", style = MaterialTheme.typography.bodySmall)
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
}

// ---------- 4. Resumen ----------

@OptIn(ExperimentalMaterial3Api::class)
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
        Text("Total a pagar: ${dinero(total, vm.conCentavos)}", style = MaterialTheme.typography.titleLarge)
        Button(
            onClick = {
                val texto = resumenTexto(reparto, total, vm.conCentavos)
                val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                context.startActivity(Intent.createChooser(envio, "Compartir cuenta"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Compartir por WhatsApp u otra app") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TarjetaPersona(d: Detalle, centavos: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(d.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(dinero(d.total, centavos), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

private fun resumenTexto(reparto: Map<String, Detalle>, total: BigDecimal, centavos: Boolean) = buildString {
    appendLine("Cuenta dividida:")
    reparto.values.forEach { appendLine("• ${it.nombre}: ${dinero(it.total, centavos)}") }
    append("Total: ${dinero(total, centavos)}")
}
