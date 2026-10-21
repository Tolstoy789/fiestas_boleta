package app.divisorcuentas.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.divisorcuentas.core.CuentaGuardada
import app.divisorcuentas.core.DatosPago
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.Mensajes
import app.divisorcuentas.core.Precios
import app.divisorcuentas.core.Rut
import app.divisorcuentas.core.Transferencia
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val LOCALE = Locale.forLanguageTag("es-CL")

fun dinero(x: BigDecimal, centavos: Boolean): String =
    NumberFormat.getCurrencyInstance(LOCALE).apply {
        minimumFractionDigits = if (centavos) 2 else 0
        maximumFractionDigits = if (centavos) 2 else 0
    }.format(x)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDivisor(vm: CuentaViewModel) {
    // Los avisos (no los errores) se van solos
    LaunchedEffect(vm.aviso) {
        if (vm.aviso != null) {
            delay(4_000)
            vm.aviso = null
        }
    }
    val titulos = mapOf(
        Pantalla.INICIO to "Nueva cuenta",
        Pantalla.BOLETA to "1. Boleta",
        Pantalla.ITEMS to "2. Ítems",
        Pantalla.ASIGNAR to "3. Quién consumió qué",
        Pantalla.RESUMEN to if (vm.repartido) "3. Cuánto paga cada uno" else "4. Cuánto paga cada uno",
        Pantalla.HISTORIAL to "Historial",
    )
    val atras = when (vm.pantalla) {
        Pantalla.INICIO -> null
        Pantalla.HISTORIAL -> Pantalla.INICIO
        Pantalla.RESUMEN -> if (vm.repartido) Pantalla.ITEMS else Pantalla.ASIGNAR
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
        // imePadding: con la pantalla de borde a borde, el teclado tapaba los campos y botones de abajo
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().padding(horizontal = 16.dp),
        ) {
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
            val ocupado = vm.ocupado
            if (ocupado != null && vm.pantalla != Pantalla.BOLETA) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Boleta.Billete)
                    Text(ocupado, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            when (vm.pantalla) {
                Pantalla.INICIO -> PantallaInicio(vm)
                Pantalla.BOLETA -> PantallaBoleta(vm)
                Pantalla.ITEMS -> PantallaItems(vm)
                Pantalla.ASIGNAR -> PantallaAsignar(vm)
                Pantalla.RESUMEN -> PantallaResumen(vm)
                Pantalla.HISTORIAL -> PantallaHistorial(vm)
            }
        }
    }
}

// ---------- 0. Inicio: quiénes y cómo se divide ----------

@Composable
private fun PantallaInicio(vm: CuentaViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Quiénes están?", style = MaterialTheme.typography.titleMedium)
        EditorParticipantes(vm)

        HorizontalDivider()
        Text("¿Cómo dividen la cuenta?", style = MaterialTheme.typography.titleMedium)
        OpcionModo(
            titulo = "Repartido",
            detalle = "El total se divide en partes iguales. Rápido, sin asignar ítems.",
            elegido = vm.repartido,
        ) { vm.repartido = true }
        OpcionModo(
            titulo = "Detallado",
            detalle = "Cada uno paga lo que consumió, con la propina en proporción.",
            elegido = !vm.repartido,
        ) { vm.repartido = false }

        Button(
            onClick = { vm.pantalla = Pantalla.BOLETA },
            enabled = vm.participantes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.participantes.isEmpty()) "Agrega al menos una persona" else "Siguiente: la boleta") }
        val guardadas = vm.historial.cuentas.size
        val porCobrar = vm.historial.cuentas.sumOf { it.pendientes().size }
        OutlinedButton(onClick = { vm.pantalla = Pantalla.HISTORIAL }, Modifier.fillMaxWidth()) {
            Text(
                when {
                    porCobrar > 0 -> "Historial ($guardadas) · $porCobrar por cobrar"
                    guardadas > 0 -> "Historial ($guardadas)"
                    else -> "Historial y respaldo"
                },
            )
        }
    }
}

@Composable
private fun OpcionModo(titulo: String, detalle: String, elegido: Boolean, alElegir: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().selectable(selected = elegido, onClick = alElegir, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = if (elegido) Boleta.Billete else Boleta.Papel),
        border = BorderStroke(1.dp, if (elegido) Boleta.Billete else Boleta.Borde),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = elegido, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Boleta.Grafito),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(titulo, fontWeight = FontWeight.SemiBold, color = if (elegido) Color.White else Boleta.Tinta)
                Text(
                    detalle, style = MaterialTheme.typography.bodySmall,
                    color = if (elegido) Color.White else Boleta.Grafito,
                )
            }
        }
    }
}

/** Agregar o quitar personas, cargar un grupo guardado y guardar los actuales como grupo. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditorParticipantes(vm: CuentaViewModel) {
    var nuevo by rememberSaveable { mutableStateOf("") }
    var nombrandoGrupo by rememberSaveable { mutableStateOf(false) }
    Column {
        val agregar = {
            vm.agregarParticipante(nuevo)
            nuevo = ""
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                nuevo, { nuevo = it }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true,
                // "Listo" en el teclado agrega a la persona y deja el campo listo para la siguiente
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (nuevo.isNotBlank()) agregar() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = agregar, enabled = nuevo.isNotBlank()) { Text("Agregar") }
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
                        label = { Text("Nombre del grupo (ej. La pega)") }, singleLine = true,
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

// ---------- 1. Boleta ----------

@Composable
private fun PantallaBoleta(vm: CuentaViewModel) {
    val context = LocalContext.current
    // Saveable: si Android recrea la pantalla mientras la cámara está abierta, no se pierde la foto
    var fotoCamara by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::procesarFoto)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) fotoCamara?.let(vm::procesarFoto)
    }
    val modelo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importarModelo)
    }
    // Boletas que llegan después del evento: una imagen o un PDF en Descargas, Drive, WhatsApp…
    val buscarArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::procesarArchivo)
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Saca una foto a la boleta con buena luz, recta y completa, o busca la imagen si te la mandaron después.")

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
                try {
                    camara.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    vm.error = "No se encontró una app de cámara. Elige la foto de la galería."
                }
            }, Modifier.fillMaxWidth()) { Text("Tomar foto") }
            OutlinedButton(
                onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.fillMaxWidth(),
            ) { Text("Elegir de la galería") }
            OutlinedButton(
                onClick = { buscarArchivo.launch(arrayOf("image/*", "application/pdf")) },
                Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Buscar imagen o PDF")
                    Text("En Descargas, Drive, correo o WhatsApp", style = MaterialTheme.typography.bodySmall)
                }
            }
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
                        isError = item.cantidad.isNotBlank() && (item.cantidad.toIntOrNull() ?: 0) < 1,
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
                onClick = vm::despuesDeItems,
                enabled = vm.items.isNotEmpty() && vm.itemsInvalidos.isEmpty(),
            ) { Text(if (vm.repartido) "Calcular" else "Siguiente") }
        }
    }
}

// ---------- 3. Asignar ----------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PantallaAsignar(vm: CuentaViewModel) {
    // Las personas ya se eligieron al inicio: el editor queda plegado para dejar espacio a los ítems
    var editarPersonas by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { editarPersonas = !editarPersonas }) {
            Text(if (editarPersonas) "Listo" else "${vm.participantes.size} personas · agregar o quitar")
        }
        if (editarPersonas) EditorParticipantes(vm)
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
        val sinMarcar = vm.items.count { vm.asignaciones[it.id].isNullOrEmpty() }
        if (sinMarcar > 0) {
            Text(
                if (sinMarcar == 1) "1 ítem sin marcar: se reparte entre todos."
                else "$sinMarcar ítems sin marcar: se reparten entre todos.",
                style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = { vm.pantalla = Pantalla.RESUMEN },
            enabled = vm.participantes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("Calcular") }
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
    // Se recalcula solo cuando cambia algo que afecta la cuenta, no en cada tecla del nombre de la cuenta
    val reparto by remember { derivedStateOf { vm.calcular() } }
    val transferencias by remember { derivedStateOf { reparto?.let(vm::transferencias).orEmpty() } }
    var editandoDatosDe by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (vm.repartido) "Repartido: el total en partes iguales" else "Detallado: cada uno paga lo que consumió",
            style = MaterialTheme.typography.labelLarge,
            color = Boleta.Billete,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                vm.propinaPct, { vm.propinaPct = it }, Modifier.width(120.dp),
                label = { Text("Propina %") }, singleLine = true,
                isError = vm.propinaPct.isNotBlank() && Precios.porcentaje(vm.propinaPct) == null,
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
        // Atajos para la propina más común en Chile (10 %) y las otras dos que más se usan
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("0", "10", "15").forEach { pct ->
                FilterChip(
                    selected = Precios.porcentaje(vm.propinaPct)?.compareTo(BigDecimal(pct)) == 0,
                    onClick = { vm.propinaPct = pct },
                    label = { Text(if (pct == "0") "Sin propina" else "$pct %") },
                )
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
                isError = vm.impuestoPct.isNotBlank() && Precios.porcentaje(vm.impuestoPct) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        AjustesCuenta(vm)
        HorizontalDivider()

        val r = reparto
        if (r == null) {
            Text(vm.motivoSinCalculo() ?: "No se pudo calcular la cuenta.", color = MaterialTheme.colorScheme.error)
            return@Column
        }
        r.values.forEach { TarjetaPersona(it, vm.conCentavos, conItems = !vm.repartido) }

        val total = r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
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
        val enJuntada = vm.boletasJuntada.isNotEmpty()
        if (enJuntada) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Juntada: ${vm.boletasJuntada.size + 1} boletas", fontWeight = FontWeight.SemiBold)
                    Text(
                        (vm.boletasJuntada.map { it.titulo } + "esta").joinToString(" + ") +
                            " · total ${dinero(vm.totalJuntada(r), vm.conCentavos)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (vm.pagador == null) "Elige quién pagó esta boleta para calcular las transferencias de toda la juntada."
                        else "Las transferencias de abajo saldan todas las boletas juntas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.pagador == null) MaterialTheme.colorScheme.error else Boleta.Grafito,
                    )
                    TextButton(onClick = vm::cerrarJuntada) { Text("Cerrar juntada") }
                }
            }
        }
        val datosPorAcreedor = vm.datosPorAcreedor(transferencias)
        val tituloMensaje = if (enJuntada) "la juntada" else vm.tituloCuenta.ifBlank { "la cuenta" }
        transferencias.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t.de} le transfiere a ${t.a}", Modifier.weight(1f))
                Text(dinero(t.monto, vm.conCentavos), style = EstiloMonto.copy(fontSize = 18.sp))
                TextButton(onClick = {
                    // Al cobrar, la cuenta queda guardada: así la deuda aparece en el historial para marcarla pagada
                    vm.guardarEnHistorial(r, avisar = false)
                    enviarWhatsApp(context, Mensajes.cobro(t, tituloMensaje, datosPorAcreedor[t.a]) { dinero(it, vm.conCentavos) })
                }) { Text("Cobrar") }
            }
        }
        // Datos para depositar de cada persona que recibe plata (en una juntada pueden ser varias)
        vm.acreedores(r).forEach { quien ->
            TarjetaDatosPago(quien, vm.datosPagoDe(quien)) { editandoDatosDe = quien }
        }

        HorizontalDivider()
        OutlinedTextField(
            vm.tituloCuenta, { vm.tituloCuenta = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre de la cuenta (opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedButton(
            onClick = { vm.agregarOtraBoleta(r) },
            enabled = vm.pagador != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.pagador == null) "Para agregar otra boleta, elige quién pagó esta" else "Agregar otra boleta a esta juntada") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.guardarEnHistorial(r) }, Modifier.weight(1f)) {
                Text("Guardar")
            }
            OutlinedButton(
                onClick = {
                    vm.guardarEnHistorial(r, avisar = false)
                    vm.exportarPdf(r) { compartirPdf(context, it) }
                },
                enabled = vm.ocupado == null,
                modifier = Modifier.weight(1f),
            ) { Text("PDF") }
            Button(
                onClick = {
                    vm.guardarEnHistorial(r, avisar = false)
                    val texto = resumenTexto(
                        vm.tituloCuenta, r, total, transferencias, vm.conCentavos,
                        datosPorAcreedor = datosPorAcreedor, repartido = vm.repartido,
                        boletasEnJuntada = vm.boletasJuntada.size + 1,
                    )
                    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                    context.startActivity(Intent.createChooser(envio, "Compartir cuenta"))
                },
                modifier = Modifier.weight(1f),
            ) { Text("Compartir") }
        }
        Spacer(Modifier.height(16.dp))
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

/** Los datos bancarios de [persona], con botones para copiarlos y editarlos. */
@Composable
private fun TarjetaDatosPago(persona: String, datos: DatosPago?, alEditar: () -> Unit) {
    val portapapeles = LocalClipboardManager.current
    val vacio = datos == null || datos.vacio
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Datos para depositar a $persona", fontWeight = FontWeight.SemiBold)
            if (datos == null || vacio) {
                Text(
                    "Agrégalos y saldrán en el PDF y en el mensaje. Quedan guardados para la próxima.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                datos.lineas().forEach { (etiqueta, valor) ->
                    Row {
                        Text(etiqueta, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
                        Text(valor, style = EstiloMonto.copy(fontSize = 14.sp, color = Boleta.Tinta))
                    }
                }
            }
            Row {
                TextButton(onClick = alEditar) { Text(if (vacio) "Agregar datos bancarios" else "Editar datos") }
                if (datos != null && !vacio) {
                    TextButton(onClick = {
                        portapapeles.setText(AnnotatedString(datos.lineas().joinToString("\n") { (k, v) -> "$k: $v" }))
                    }) { Text("Copiar") }
                }
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
        title = { Text("Datos para depositar a $persona") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    datos.titular, { datos = datos.copy(titular = it) }, label = { Text("Nombre del titular") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    datos.rut, { datos = datos.copy(rut = it) }, label = { Text("RUT") }, singleLine = true,
                    isError = rutMalo,
                    supportingText = if (rutMalo) ({ Text("El dígito verificador no calza; revísalo.") }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    datos.banco, { datos = datos.copy(banco = it) }, label = { Text("Banco") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    datos.tipoCuenta, { datos = datos.copy(tipoCuenta = it) },
                    label = { Text("Tipo de cuenta") }, singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIPOS_DE_CUENTA.forEach { (etiqueta, tipo) ->
                        FilterChip(
                            selected = datos.tipoCuenta.equals(tipo, ignoreCase = true),
                            onClick = { datos = datos.copy(tipoCuenta = tipo) },
                            label = { Text(etiqueta) },
                        )
                    }
                }
                OutlinedTextField(
                    datos.numero, { datos = datos.copy(numero = it) }, label = { Text("Número de cuenta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    datos.correo, { datos = datos.copy(correo = it) }, label = { Text("Correo") }, singleLine = true,
                    isError = correoMalo,
                    supportingText = if (correoMalo) ({ Text("Parece que al correo le falta algo.") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
        // Se puede guardar igual (el aviso es para revisar); el RUT válido queda con puntos y guion
        confirmButton = {
            TextButton(onClick = { alGuardar(datos.copy(rut = Rut.formatear(datos.rut), correo = datos.correo.trim())) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// ---------- Historial ----------

@Composable
private fun PantallaHistorial(vm: CuentaViewModel) {
    val context = LocalContext.current
    val fecha = remember { SimpleDateFormat("d MMM yyyy, HH:mm", LOCALE) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportarRespaldo)
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importarRespaldo)
    }
    // Borrar no se puede deshacer: se pregunta antes
    var cuentaPorBorrar by remember { mutableStateOf<CuentaGuardada?>(null) }
    var grupoPorBorrar by remember { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportar.launch("respaldo-divisor-${SimpleDateFormat("yyyy-MM-dd", LOCALE).format(Date())}.json") },
                    modifier = Modifier.weight(1f),
                ) { Text("Exportar respaldo") }
                OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f)) {
                    Text("Importar")
                }
            }
            Text(
                "El respaldo guarda cuentas, grupos y datos bancarios. Déjalo en Drive para pasarlo a otro teléfono.",
                style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito,
            )
        }
        if (vm.historial.cuentas.isEmpty()) {
            item { Text("Aún no hay cuentas guardadas.") }
        }
        items(vm.historial.cuentas, key = { it.creada }) { c ->
            val total = c.totales.values.mapNotNull { it.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            val deudas = c.deudas()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.clickable(onClickLabel = "Abrir cuenta") { vm.abrirDelHistorial(c) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${fecha.format(Date(c.creada))} · ${c.participantes.joinToString(", ")}" +
                                    when {
                                        c.parteDeJuntada -> " · parte de una juntada"
                                        c.repartido -> " · repartido"
                                        else -> ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(dinero(total, c.conCentavos), style = EstiloMonto)
                        TextButton(
                            onClick = { cuentaPorBorrar = c },
                            modifier = Modifier.semantics { contentDescription = "Borrar ${c.titulo}" },
                        ) { Text("✕") }
                    }
                    if (deudas.isNotEmpty()) {
                        val pendientes = deudas.count { it.de !in c.pagados }
                        Text(
                            if (pendientes == 0) "Todos pagaron" else "Faltan $pendientes por pagar",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (pendientes == 0) Boleta.Billete else Boleta.Tinta,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    deudas.forEach { t ->
                        val pagado = t.de in c.pagados
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pagado, onCheckedChange = { vm.marcarPagado(c.creada, t.de, it) })
                            Text(
                                "${t.de} → ${t.a}", Modifier.weight(1f),
                                color = if (pagado) Boleta.Grafito else Boleta.Tinta,
                                textDecoration = if (pagado) TextDecoration.LineThrough else null,
                            )
                            Text(dinero(t.monto, c.conCentavos), style = EstiloMonto.copy(fontSize = 14.sp))
                            if (!pagado) {
                                TextButton(onClick = {
                                    enviarWhatsApp(
                                        context,
                                        Mensajes.cobro(t, c.titulo, vm.datosPagoDe(t.a)?.takeUnless { it.vacio }) { dinero(it, c.conCentavos) },
                                    )
                                }) { Text("Cobrar") }
                            }
                        }
                    }
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
                        onClick = { grupoPorBorrar = g.nombre },
                        modifier = Modifier.semantics { contentDescription = "Borrar grupo ${g.nombre}" },
                    ) { Text("✕") }
                }
            }
        }
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

@Composable
private fun Confirmar(titulo: String, detalle: String, alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { Text(detalle) },
        confirmButton = {
            TextButton(onClick = { alConfirmar(); alCerrar() }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun TarjetaPersona(d: Detalle, centavos: Boolean, conItems: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(d.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (d.invitado) Text("Invitado", style = EstiloMonto.copy(fontSize = 18.sp))
                else Text(dinero(d.total, centavos), style = EstiloMonto.copy(fontSize = 20.sp))
            }
            if (conItems) d.items.forEach { (nombre, monto) ->
                Text("• $nombre: ${dinero(monto, centavos)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                buildString {
                    append("Consumo ${dinero(d.subtotal, centavos)}")
                    if (d.descuento.signum() != 0) append(" − descuento ${dinero(d.descuento, centavos)}")
                    append(" + propina ${dinero(d.propina, centavos)}")
                    if (d.impuesto.signum() != 0) append(" + impuesto ${dinero(d.impuesto, centavos)}")
                    if (d.aporteInvitados.signum() != 0) append(" + invitados ${dinero(d.aporteInvitados, centavos)}")
                    if (d.invitado) append(". Lo cubre el resto.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Abre el menú de compartir con el PDF (WhatsApp, correo, guardar en Archivos…). */
private fun compartirPdf(context: Context, archivo: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
    val envio = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    envio.clipData = ClipData.newRawUri(archivo.name, uri)
    context.startActivity(Intent.createChooser(envio, "Compartir detalle en PDF"))
}

private fun resumenTexto(
    titulo: String,
    reparto: Map<String, Detalle>,
    total: BigDecimal,
    transferencias: List<Transferencia>,
    centavos: Boolean,
    datosPorAcreedor: Map<String, DatosPago>,
    repartido: Boolean,
    boletasEnJuntada: Int,
) = buildString {
    if (titulo.isNotBlank()) appendLine(titulo.trim())
    appendLine(if (repartido) "Cuenta repartida en partes iguales:" else "Cuenta dividida:")
    reparto.values.forEach {
        appendLine(if (it.invitado) "• ${it.nombre}: invitado" else "• ${it.nombre}: ${dinero(it.total, centavos)}")
    }
    append("Total: ${dinero(total, centavos)}")
    if (transferencias.isNotEmpty()) {
        appendLine()
        appendLine()
        appendLine(
            if (boletasEnJuntada > 1) "Transferencias (saldan las $boletasEnJuntada boletas de la juntada):"
            else "Transferencias:",
        )
        transferencias.forEach { appendLine("• ${it.de} → ${it.a}: ${dinero(it.monto, centavos)}") }
    }
    datosPorAcreedor.forEach { (persona, datos) ->
        appendLine()
        appendLine("Datos para depositar a $persona:")
        datos.lineas().forEach { (etiqueta, valor) -> appendLine("$etiqueta: $valor") }
    }
}.trimEnd()

/** Abre WhatsApp con el mensaje listo; si no está instalado, el menú de compartir. */
private fun enviarWhatsApp(context: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    try {
        context.startActivity(Intent(envio).setPackage("com.whatsapp"))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(envio, "Enviar cobro"))
    }
}

/** Descuento, invitados y quién no pone propina; plegado para no estorbar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AjustesCuenta(vm: CuentaViewModel) {
    val activos = vm.invitados.size + vm.sinPropina.size + if (vm.descuento.isBlank()) 0 else 1
    var abierto by rememberSaveable { mutableStateOf(activos > 0) }
    TextButton(onClick = { abierto = !abierto }) {
        Text((if (abierto) "Ocultar" else "Invitados, descuento y propina por persona") + if (activos > 0 && !abierto) " ($activos)" else "")
    }
    if (!abierto) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            vm.descuento, { vm.descuento = it }, Modifier.fillMaxWidth(),
            label = { Text("Descuento o cupón ($)") }, singleLine = true,
            isError = vm.descuento.isNotBlank() && Precios.parsear(vm.descuento) == null,
            supportingText = { Text("Se resta antes de la propina, en proporción a lo que consumió cada uno.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text("Invitados (no pagan, lo suyo se reparte entre el resto)", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.participantes.forEach { p ->
                FilterChip(selected = p in vm.invitados, onClick = { vm.alternarInvitado(p) }, label = { Text(p) })
            }
        }
        Text("No ponen propina", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.participantes.forEach { p ->
                FilterChip(selected = p in vm.sinPropina, onClick = { vm.alternarSinPropina(p) }, label = { Text(p) })
            }
        }
    }
}
