package app.divisorcuentas.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.divisorcuentas.core.DatosPago
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.Transferencia
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

internal val LOCALE: Locale = Locale.forLanguageTag("es-CL")

fun dinero(x: BigDecimal, centavos: Boolean): String =
    NumberFormat.getCurrencyInstance(LOCALE).apply {
        minimumFractionDigits = if (centavos) 2 else 0
        maximumFractionDigits = if (centavos) 2 else 0
    }.format(x)

/** En qué paso de la cuenta está cada pantalla ("Paso 2 de 4"); null si no es parte de los pasos. */
internal fun pasoDe(p: Pantalla, repartido: Boolean): Pair<Int, Int>? {
    val total = if (repartido) 3 else 4
    return when (p) {
        Pantalla.BOLETA -> 1 to total
        Pantalla.ITEMS -> 2 to total
        Pantalla.ASIGNAR -> 3 to total
        Pantalla.RESUMEN -> total to total
        Pantalla.INICIO, Pantalla.HISTORIAL -> null
    }
}

@Composable
fun AppDivisor(vm: CuentaViewModel) {
    // Los avisos (no los errores) se van solos; lo borrado se puede deshacer por unos segundos
    LaunchedEffect(vm.aviso) {
        if (vm.aviso != null) {
            delay(4_000)
            vm.aviso = null
        }
    }
    LaunchedEffect(vm.borrado) {
        if (vm.borrado != null) {
            delay(5_000)
            vm.olvidarBorrado()
        }
    }
    // Al cambiar de pantalla ya no se ofrece deshacer: lo borrado era de la otra
    LaunchedEffect(vm.pantalla) { vm.olvidarBorrado() }

    val atras = when (vm.pantalla) {
        Pantalla.INICIO -> null
        Pantalla.HISTORIAL -> Pantalla.INICIO
        Pantalla.RESUMEN -> if (vm.repartido) Pantalla.ITEMS else Pantalla.ASIGNAR
        else -> Pantalla.entries[vm.pantalla.ordinal - 1]
    }
    BackHandler(enabled = atras != null) { atras?.let { vm.pantalla = it } }
    val volver = { atras?.let { vm.pantalla = it }; Unit }

    // Cada persona con su color, el mismo en todas las pantallas de esta cuenta
    val personas = vm.participantes.toList()
    val colores = remember(personas) { coloresPersonas(personas) }

    CompositionLocalProvider(LocalColoresPersona provides colores) {
        Box(Modifier.fillMaxSize().background(Boleta.Papel)) {
            AnimatedContent(
                targetState = vm.pantalla,
                transitionSpec = {
                    // Hacia adelante entra desde la derecha; al volver, desde la izquierda
                    val sentido = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it * sentido / 3 } + fadeIn(tween(260, 80))) togetherWith
                        (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it * sentido / 4 } + fadeOut(tween(180))) using
                        SizeTransform(clip = false)
                },
                label = "pantalla",
            ) { pantalla ->
                // El teclado empuja la pantalla hacia arriba: así no tapa los campos ni el botón de abajo
                Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                    when (pantalla) {
                        Pantalla.INICIO -> PantallaInicio(vm)
                        Pantalla.BOLETA -> PantallaBoleta(vm, volver)
                        Pantalla.ITEMS -> PantallaItems(vm, volver)
                        Pantalla.ASIGNAR -> PantallaAsignar(vm, volver)
                        Pantalla.RESUMEN -> PantallaResumen(vm, volver)
                        Pantalla.HISTORIAL -> PantallaHistorial(vm, volver)
                    }
                }
            }
            Avisos(vm, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Errores, avisos, trabajo en curso y "deshacer", flotando arriba de la pantalla. */
@Composable
private fun Avisos(vm: CuentaViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvisoAnimado(vm.error, TipoAviso.ERROR, accion = "OK", alAccion = { vm.error = null })
        // En la pantalla de la boleta el trabajo en curso ya se ve en grande
        AvisoAnimado(vm.ocupado.takeIf { vm.pantalla != Pantalla.BOLETA }, TipoAviso.TRABAJANDO)
        AvisoAnimado(vm.borrado?.mensaje, TipoAviso.DESHACER, accion = "Deshacer", alAccion = vm::deshacerBorrado)
        AvisoAnimado(vm.aviso, TipoAviso.BIEN, accion = "OK", alAccion = { vm.aviso = null })
    }
}

/** Abre el menú de compartir con el PDF (WhatsApp, correo, guardar en Archivos…). */
internal fun compartirPdf(context: Context, archivo: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
    val envio = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    envio.clipData = ClipData.newRawUri(archivo.name, uri)
    context.startActivity(Intent.createChooser(envio, "Compartir detalle en PDF"))
}

/** Abre el menú de compartir con el resumen en texto. */
internal fun compartirTexto(context: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    context.startActivity(Intent.createChooser(envio, "Compartir cuenta"))
}

internal fun resumenTexto(
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
internal fun enviarWhatsApp(context: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    try {
        context.startActivity(Intent(envio).setPackage("com.whatsapp"))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(envio, "Enviar cobro"))
    }
}
