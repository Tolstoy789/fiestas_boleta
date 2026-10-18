package app.divisorcuentas.core

import java.math.BigDecimal

/** Textos listos para mandar por WhatsApp. */
object Mensajes {

    /**
     * Mensaje para cobrarle a una persona: cuánto, a quién y los datos para depositar.
     * [plata] da formato a los montos (ej. "$13.470").
     */
    fun cobro(
        t: Transferencia,
        titulo: String,
        datos: DatosPago?,
        plata: (BigDecimal) -> String,
    ): String = buildString {
        append("Hola ${t.de}! De \"$titulo\" te toca transferirle ${plata(t.monto)} a ${t.a}.")
        val lineas = datos?.lineas().orEmpty()
        if (lineas.isNotEmpty()) {
            append("\n\nDatos para depositar:")
            lineas.forEach { (k, v) -> append("\n$k: $v") }
        }
        append("\n\nGracias!")
    }
}
