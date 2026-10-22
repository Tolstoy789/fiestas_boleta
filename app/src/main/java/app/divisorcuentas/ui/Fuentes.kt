package app.divisorcuentas.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.divisorcuentas.R

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
