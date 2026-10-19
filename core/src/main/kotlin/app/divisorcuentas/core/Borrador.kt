package app.divisorcuentas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal

/** [BoletaJuntada] con los montos como texto, para guardarla en JSON. */
@Serializable
data class BoletaJuntadaGuardada(
    val titulo: String,
    val totales: Map<String, String>,
    val pagador: String? = null,
)

fun BoletaJuntada.guardada() = BoletaJuntadaGuardada(titulo, totales.mapValues { it.value.toPlainString() }, pagador)

fun BoletaJuntadaGuardada.boleta() =
    BoletaJuntada(titulo, totales.mapValues { it.value.toBigDecimalOrNull() ?: BigDecimal.ZERO }, pagador)

/**
 * La cuenta en curso, guardada cuando la app pasa a segundo plano. Si Android
 * la cierra (por ejemplo mientras se manda un cobro por WhatsApp), al volver
 * se sigue donde se quedó.
 */
@Serializable
data class Borrador(
    /** Cuándo se guardó, en milisegundos desde 1970. */
    val guardado: Long,
    /** Nombre de la pantalla en que estaba. */
    val pantalla: String,
    val cuenta: CuentaGuardada,
    /** Id de la cuenta en el historial, si ya se había guardado ahí. */
    val cuentaActual: Long? = null,
    val juntada: List<BoletaJuntadaGuardada> = emptyList(),
    val textoOcr: String = "",
    val totalImpreso: String? = null,
) {
    companion object {
        /** Pasado este tiempo, al abrir la app solo se recuperan las personas y el modo. */
        const val VIGENCIA_MS = 6 * 60 * 60 * 1000L

        private val json = Json { ignoreUnknownKeys = true }

        fun aJson(b: Borrador): String = json.encodeToString(b)

        fun desdeJson(texto: String?): Borrador? =
            texto?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<Borrador>(it) }.getOrNull() }
    }

    fun vigente(ahora: Long): Boolean = ahora - guardado in 0 until VIGENCIA_MS
}
