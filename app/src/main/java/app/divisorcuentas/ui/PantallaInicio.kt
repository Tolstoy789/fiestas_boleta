package app.divisorcuentas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 0. Inicio: quiénes y cómo se divide ----------

@Composable
internal fun PantallaInicio(vm: CuentaViewModel) {
    val porCobrar = vm.historial.cuentas.sumOf { it.pendientes().size }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Marca(Modifier.weight(1f))
            BotonHistorial(porCobrar) { vm.pantalla = Pantalla.HISTORIAL }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Column {
                Text("¿Quiénes están?", style = MaterialTheme.typography.headlineMedium, color = Boleta.Tinta)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Agrega a las personas de la cuenta o elige un grupo guardado.",
                    style = MaterialTheme.typography.bodyMedium, color = Boleta.Grafito,
                )
            }
            EditorParticipantes(vm)
            Spacer(Modifier.height(4.dp))
            Seccion("¿Cómo dividen?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaModo(
                    titulo = "Repartido",
                    detalle = "El total en partes iguales. Rápido, sin asignar ítems.",
                    icono = Iconos.Repartido,
                    elegido = vm.repartido,
                    modifier = Modifier.weight(1f),
                ) { vm.repartido = true }
                TarjetaModo(
                    titulo = "Detallado",
                    detalle = "Cada uno paga lo suyo, con la propina en proporción.",
                    icono = Iconos.Detallado,
                    elegido = !vm.repartido,
                    modifier = Modifier.weight(1f),
                ) { vm.repartido = false }
            }
            Spacer(Modifier.height(12.dp))
        }
        BarraAccion {
            val n = vm.participantes.size
            BotonPrincipal(
                texto = when (n) {
                    0 -> "Agrega al menos una persona"
                    1 -> "Seguir con 1 persona"
                    else -> "Seguir con $n personas"
                },
                alTocar = { vm.pantalla = Pantalla.BOLETA },
                modifier = Modifier.weight(1f),
                habilitado = n > 0,
                icono = Iconos.Siguiente.takeIf { n > 0 },
            )
        }
    }
}

/** El nombre de la app, como en la animación de inicio. */
@Composable
private fun Marca(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("divisor")
            withStyle(SpanStyle(color = Boleta.Billete)) { append(".") }
        },
        modifier,
        fontFamily = FuenteMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Boleta.Tinta,
    )
}

/** Acceso al historial, con cuántos cobros quedan pendientes. */
@Composable
private fun BotonHistorial(porCobrar: Int, alTocar: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Boleta.Tarjeta)
            .border(1.dp, Boleta.Hilo, CircleShape)
            .clickable(role = Role.Button, onClick = alTocar)
            .padding(start = 12.dp, end = if (porCobrar > 0) 6.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Reloj, contentDescription = null, Modifier.size(19.dp), tint = Boleta.Tinta)
        Spacer(Modifier.width(7.dp))
        Text("Historial", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = Boleta.Tinta)
        if (porCobrar > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.heightIn(min = 28.dp).clip(CircleShape).background(Boleta.Billete).padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$porCobrar por cobrar",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = Boleta.Blanco,
                )
            }
        }
    }
}

@Composable
private fun TarjetaModo(
    titulo: String,
    detalle: String,
    icono: ImageVector,
    elegido: Boolean,
    modifier: Modifier = Modifier,
    alElegir: () -> Unit,
) {
    val borde by animateColorAsState(if (elegido) Boleta.Tinta else Boleta.Hilo, label = "borde")
    val grosor by animateDpAsState(if (elegido) 2.dp else 1.dp, label = "grosor")
    val forma = RoundedCornerShape(22.dp)
    Box(
        modifier
            .heightIn(min = 158.dp)
            .clip(forma)
            .background(Boleta.Tarjeta)
            .border(grosor, borde, forma)
            .selectable(selected = elegido, role = Role.RadioButton, onClick = alElegir)
            .padding(16.dp),
    ) {
        Column {
            IconoEnCirculo(
                icono,
                fondo = if (elegido) Boleta.Menta else Boleta.Carton,
                tinte = if (elegido) Boleta.Billete else Boleta.Grafito,
            )
            Spacer(Modifier.height(14.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
            Spacer(Modifier.height(4.dp))
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
        }
        MarcaElegido(elegido, Modifier.align(Alignment.TopEnd))
    }
}

/** Agregar o quitar personas, cargar un grupo guardado y guardar los actuales como grupo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorParticipantes(vm: CuentaViewModel, modifier: Modifier = Modifier) {
    var nuevo by rememberSaveable { mutableStateOf("") }
    var nombrandoGrupo by rememberSaveable { mutableStateOf(false) }
    // Las personas que ya estaban al abrir la pantalla aparecen quietas; las nuevas, con un salto
    val alAbrir = remember { vm.participantes.toSet() }
    val agregar = {
        if (nuevo.isNotBlank()) {
            vm.agregarParticipante(nuevo)
            nuevo = ""
        }
    }
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(
            nuevo, { nuevo = it },
            Modifier.fillMaxWidth(),
            ejemplo = "Escribe un nombre",
            // "Listo" en el teclado agrega a la persona y deja el campo listo para la siguiente
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            acciones = KeyboardActions(onDone = { agregar() }),
            alFinal = {
                BotonIcono(
                    Iconos.Mas, "Agregar", agregar,
                    Modifier.padding(end = 6.dp),
                    tamano = 40.dp,
                    tinte = Boleta.Papel,
                    fondo = if (nuevo.isBlank()) Boleta.Borde else Boleta.Tinta,
                    borde = null,
                    habilitado = nuevo.isNotBlank(),
                )
            },
        )
        val grupos = vm.historial.grupos
        if (grupos.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grupos.forEach { g ->
                    ChipOpcion("${g.nombre} · ${g.miembros.size}", elegido = false, alTocar = { vm.usarGrupo(g) }, icono = Iconos.Personas)
                }
            }
        }
        if (vm.participantes.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 84.dp).bordePunteado(radio = 20.dp).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Todavía no hay nadie. Escribe un nombre y toca +.",
                    style = MaterialTheme.typography.bodyMedium, color = Boleta.Grafito,
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.participantes.forEach { p ->
                    ChipPersona(
                        p,
                        Modifier.aparecer(p !in alAbrir),
                        alQuitar = { vm.borrarParticipante(p) },
                    )
                }
            }
        }
        if (vm.participantes.size >= 2) {
            BotonTexto("Guardar como grupo", { nombrandoGrupo = true }, icono = Iconos.Guardar)
        }
    }

    if (nombrandoGrupo) {
        var nombreGrupo by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nombrandoGrupo = false },
            containerColor = Boleta.Tarjeta,
            title = { Text("Guardar grupo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.participantes.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Boleta.Grafito)
                    CampoTexto(
                        nombreGrupo, { nombreGrupo = it },
                        etiqueta = "Nombre del grupo", ejemplo = "La pega",
                        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.guardarGrupo(nombreGrupo); nombrandoGrupo = false },
                    enabled = nombreGrupo.isNotBlank(),
                ) { Text("Guardar", color = if (nombreGrupo.isNotBlank()) Boleta.Billete else Boleta.Grafito) }
            },
            dismissButton = { TextButton(onClick = { nombrandoGrupo = false }) { Text("Cancelar", color = Boleta.Grafito) } },
        )
    }
}

/** Aparece con un pequeño salto (solo si [animar]). */
internal fun Modifier.aparecer(animar: Boolean): Modifier = composed {
    val escala = remember { Animatable(if (animar) 0.5f else 1f) }
    LaunchedEffect(Unit) { escala.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    graphicsLayer {
        scaleX = escala.value
        scaleY = escala.value
        alpha = ((escala.value - 0.5f) * 2f).coerceIn(0f, 1f)
    }
}
