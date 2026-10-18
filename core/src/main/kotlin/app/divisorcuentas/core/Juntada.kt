package app.divisorcuentas.core

import java.math.BigDecimal

/** Una boleta ya calculada dentro de una juntada: cuánto le toca a cada uno y quién la pagó. */
data class BoletaJuntada(
    val titulo: String,
    val totales: Map<String, BigDecimal>,
    val pagador: String?,
)

/**
 * Varias boletas de una misma salida (el bar y después la comida), pagadas por
 * personas distintas. En vez de saldar cada boleta por separado, se suma lo que
 * le toca a cada uno y lo que puso cada uno, y se calcula el saldo neto: menos
 * transferencias.
 */
object Juntada {

    fun aPagar(boletas: List<BoletaJuntada>): Map<String, BigDecimal> = sumar(boletas.map { it.totales })

    /** Lo que puso cada uno: el total de cada boleta que pagó. Las boletas sin pagador no cuentan. */
    fun pagado(boletas: List<BoletaJuntada>): Map<String, BigDecimal> = sumar(
        boletas.mapNotNull { b -> b.pagador?.let { mapOf(it to b.totales.values.fold(BigDecimal.ZERO, BigDecimal::add)) } },
    )

    fun transferencias(boletas: List<BoletaJuntada>): List<Transferencia> =
        Saldos.transferencias(aPagar(boletas), pagado(boletas))

    private fun sumar(mapas: List<Map<String, BigDecimal>>): Map<String, BigDecimal> {
        val r = linkedMapOf<String, BigDecimal>()
        mapas.forEach { m -> m.forEach { (p, x) -> r[p] = (r[p] ?: BigDecimal.ZERO) + x } }
        return r
    }
}
