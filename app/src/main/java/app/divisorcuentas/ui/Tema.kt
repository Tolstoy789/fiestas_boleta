package app.divisorcuentas.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diseño "Boleta": papel y tinta, letra de máquina para los montos y un solo
 * acento verde para el dinero. Cada persona tiene además su color, para
 * reconocerla de un vistazo en todas las pantallas.
 */
object Boleta {
    val Tinta = Color(0xFF1B1F24)
    val Papel = Color(0xFFF6F3EC)
    val Billete = Color(0xFF1F7A52)
    val Corte = Color(0xFF3FBF83)
    val Grafito = Color(0xFF5D636B)
    val Borde = Color(0xFFD9D3C5)
    val Blanco = Color(0xFFFFFFFF)

    /** Fondo de las tarjetas: un blanco cálido sobre el papel. */
    val Tarjeta = Color(0xFFFFFDF8)
    /** Bordes finos de tarjetas, campos y separadores. */
    val Hilo = Color(0xFFE7E1D3)
    /** Papel un poco más oscuro, para fondos de campos y controles. */
    val Carton = Color(0xFFEFEADF)
    /** Verde suave: lo que cuadra, lo elegido, los avisos buenos. */
    val Menta = Color(0xFFE3F1E9)
    /** Rojo de timbre para errores y para borrar. */
    val Timbre = Color(0xFFB3372B)
    val TimbreSuave = Color(0xFFF8E4DF)
    /** Ámbar para lo que conviene revisar. */
    val Ambar = Color(0xFF8A5A00)
    val AmbarSuave = Color(0xFFFBEFD5)

    /** Un color por persona: oscuros, para que las iniciales en blanco se lean bien. */
    val Personas = listOf(
        Color(0xFF1F7A52), // verde
        Color(0xFFC2603C), // terracota
        Color(0xFF3B6EA8), // azul
        Color(0xFF8A5CB8), // morado
        Color(0xFFA87A1C), // ocre
        Color(0xFF2F8F8B), // turquesa
        Color(0xFFB54A6A), // frambuesa
        Color(0xFF5D636B), // grafito
    )
}

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
    surfaceVariant = Boleta.Carton,
    onSurfaceVariant = Boleta.Grafito,
    surfaceContainer = Boleta.Tarjeta,
    surfaceContainerLow = Boleta.Tarjeta,
    surfaceContainerHigh = Boleta.Tarjeta,
    surfaceContainerHighest = Boleta.Tarjeta,
    surfaceContainerLowest = Boleta.Tarjeta,
    secondaryContainer = Boleta.Menta,
    onSecondaryContainer = Boleta.Tinta,
    error = Boleta.Timbre,
    errorContainer = Boleta.TimbreSuave,
    outline = Boleta.Borde,
    outlineVariant = Boleta.Hilo,
)

private val tipografia = Typography().let { base ->
    fun TextStyle.sans() = copy(fontFamily = FuenteSans)
    Typography(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans().copy(fontWeight = FontWeight.Bold),
        // Títulos de pantalla: grandes y apretados, como un encabezado impreso
        headlineMedium = base.headlineMedium.sans().copy(
            fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp,
        ),
        headlineSmall = base.headlineSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.sans().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans().copy(letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.sans().copy(letterSpacing = 0.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.sans().copy(letterSpacing = 0.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontFamily = FuenteMono),
        labelSmall = base.labelSmall.copy(fontFamily = FuenteMono),
    )
}

private val formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TemaBoleta(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, typography = tipografia, shapes = formas, content = contenido)
}
