package app.divisorcuentas.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

private const val DURACION_MS = 2600

/**
 * Animación de inicio del diseño "Boleta":
 *  1. el ticket sale impreso por la ranura, a saltos como una impresora térmica;
 *  2. una línea verde lo corta de arriba abajo;
 *  3. las dos mitades se separan;
 *  4. aparece el nombre.
 *
 * Tocar la pantalla la salta. Al terminar llama a [alTerminar].
 */
@Composable
fun IntroBoleta(alTerminar: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(DURACION_MS, easing = LinearEasing))
        alTerminar()
    }
    val p = t.value

    Box(
        Modifier
            .fillMaxSize()
            .background(Boleta.Papel)
            .clickable(onClickLabel = "Saltar animación") { alTerminar() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 220.dp, height = 330.dp)) { dibujarTicket(p) }
            Spacer(Modifier.height(32.dp))
            val nombre = fase(p, 0.70f, 0.85f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = nombre
                    translationY = (1f - nombre) * 10.dp.toPx()
                },
            ) {
                Text(
                    buildAnnotatedString {
                        append("divisor")
                        withStyle(SpanStyle(color = Boleta.Billete)) { append(".") }
                    },
                    fontFamily = FuenteMono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Boleta.Tinta,
                )
                Text("la cuenta, clara", fontFamily = FuenteSans, fontSize = 17.sp, color = Boleta.Grafito)
            }
        }
    }
}

/** Avance de 0 a 1 entre los instantes [desde] y [hasta] del total, con suavizado. */
private fun fase(p: Float, desde: Float, hasta: Float): Float =
    FastOutSlowInEasing.transform(((p - desde) / (hasta - desde)).coerceIn(0f, 1f))

private fun DrawScope.dibujarTicket(p: Float) {
    val u = size.width / 220f // 1 unidad del diseño = 1 px a 220 de ancho
    val ancho = 188 * u
    val alto = 300 * u
    val izquierda = (size.width - ancho) / 2
    val arriba = 14 * u

    // 1. Impresión a saltos (9 pasos) durante el primer 40 %.
    val impreso = floor(fase(p, 0f, 0.40f) * 9f) / 9f
    // 2. Corte verde; 3. separación de las mitades.
    val corte = fase(p, 0.42f, 0.56f)
    val separa = fase(p, 0.58f, 0.70f)

    clipRect(left = 0f, top = arriba, right = size.width, bottom = size.height) {
        translate(left = izquierda, top = arriba - alto * (1f - impreso)) {
            val mitad = ancho / 2
            rotate(-3f * separa, pivot = Offset(mitad, 0f)) {
                translate(left = -22 * u * separa) { mitadTicket(u, mitad, alto, derecha = false) }
            }
            rotate(3f * separa, pivot = Offset(mitad, 0f)) {
                translate(left = mitad + 22 * u * separa) { mitadTicket(u, mitad, alto, derecha = true) }
            }
            // Línea de corte punteada
            val largo = (alto + 12 * u) * corte
            var y = -6 * u
            while (y < -6 * u + largo) {
                val tramo = minOf(8 * u, -6 * u + largo - y)
                drawRect(Boleta.Corte, Offset(mitad - 2 * u, y), Size(4 * u, tramo))
                y += 14 * u
            }
        }
    }
    // Ranura de la impresora
    drawRoundRect(
        Boleta.Tinta, Offset(izquierda - 16 * u, 6 * u), Size(ancho + 32 * u, 16 * u), CornerRadius(8 * u),
    )
}

private fun DrawScope.mitadTicket(u: Float, ancho: Float, alto: Float, derecha: Boolean) {
    // Papel con borde inferior dentado
    val dientes = 6
    val paso = ancho / dientes
    val papel = Path().apply {
        moveTo(0f, 0f)
        lineTo(ancho, 0f)
        lineTo(ancho, alto - 8 * u)
        for (i in 1..dientes) {
            val x = ancho - paso * i
            lineTo(x + paso / 2, alto)
            lineTo(x, alto - 8 * u)
        }
        close()
    }
    drawPath(papel, Color.White)

    // Renglones: nombres a la izquierda, precios a la derecha
    fun renglon(x: Float, y: Float, w: Float, h: Float, color: Color) =
        drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(1 * u))
    if (!derecha) {
        renglon(16f, 34f, 62f, 7f, Boleta.Tinta)
        listOf(48f, 56f, 40f, 52f).forEachIndexed { i, w -> renglon(16f, 66f + 24f * i, w, 6f, Boleta.Grafito) }
        renglon(16f, 190f, 44f, 9f, Boleta.Tinta)
    } else {
        listOf(38f, 32f, 38f, 32f).forEachIndexed { i, w -> renglon(78f - w, 66f + 24f * i, w, 6f, Boleta.Grafito) }
        renglon(26f, 190f, 52f, 9f, Boleta.Billete)
    }
}
