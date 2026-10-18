package app.divisorcuentas.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.divisorcuentas.R

/**
 * Diseño "Boleta": papel y tinta, letra de máquina para los montos y un solo
 * acento verde para el dinero.
 */
object Boleta {
    val Tinta = Color(0xFF1B1F24)
    val Papel = Color(0xFFF6F3EC)
    val Billete = Color(0xFF1F7A52)
    val Corte = Color(0xFF3FBF83)
    val Grafito = Color(0xFF5D636B)
    val Borde = Color(0xFFD9D3C5)
    val Blanco = Color(0xFFFFFFFF)
}

@OptIn(ExperimentalTextApi::class)
private fun plexSans(peso: FontWeight) = Font(
    R.font.ibm_plex_sans,
    weight = peso,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso.weight)),
)

/** IBM Plex Sans (fuente variable) para textos. */
val FuenteSans = FontFamily(
    plexSans(FontWeight.Normal),
    plexSans(FontWeight.Medium),
    plexSans(FontWeight.SemiBold),
    plexSans(FontWeight.Bold),
)

/** IBM Plex Mono para montos, como un ticket impreso. */
val FuenteMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Estilo de los montos en pantalla. */
val EstiloMonto = TextStyle(fontFamily = FuenteMono, fontWeight = FontWeight.SemiBold, color = Boleta.Billete)

private val colores = lightColorScheme(
    primary = Boleta.Tinta,
    onPrimary = Boleta.Papel,
    secondary = Boleta.Billete,
    onSecondary = Boleta.Blanco,
    tertiary = Boleta.Corte,
    onTertiary = Boleta.Tinta,
    background = Boleta.Papel,
    onBackground = Boleta.Tinta,
    surface = Boleta.Papel,
    onSurface = Boleta.Tinta,
    surfaceVariant = Boleta.Blanco,
    onSurfaceVariant = Boleta.Grafito,
    surfaceContainer = Boleta.Blanco,
    surfaceContainerLow = Boleta.Blanco,
    surfaceContainerHigh = Boleta.Blanco,
    surfaceContainerHighest = Boleta.Blanco,
    surfaceContainerLowest = Boleta.Blanco,
    secondaryContainer = Color(0xFFDDEFE5),
    onSecondaryContainer = Boleta.Tinta,
    outline = Boleta.Borde,
    outlineVariant = Boleta.Borde,
)

private val tipografia = Typography().let { base ->
    fun TextStyle.sans() = copy(fontFamily = FuenteSans)
    Typography(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans().copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.sans().copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelMedium = base.labelMedium.copy(fontFamily = FuenteMono),
        labelSmall = base.labelSmall.copy(fontFamily = FuenteMono),
    )
}

private val formas = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun TemaBoleta(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, typography = tipografia, shapes = formas, content = contenido)
}
