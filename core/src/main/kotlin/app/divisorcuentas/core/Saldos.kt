package app.divisorcuentas.core

import java.math.BigDecimal

/** "[de] le transfiere [monto] a [a]". */
data class Transferencia(val de: String, val a: String, val monto: BigDecimal)

/**
 * Quién le debe a quién, una vez que alguien (o varios) pagó la cuenta.
 */
object Saldos {

    /**
     * Calcula las transferencias para que cada uno termine pagando lo suyo, con
     * el menor número de movimientos posible en la práctica: los que deben más le
     * pagan primero a los que pusieron más.
     *
     * @param aPagar Lo que le corresponde pagar a cada uno (el resultado de [DivisorCuenta]).
     * @param pagado Lo que puso cada uno en la caja. Quien no aparece, no puso nada.
     */
    fun transferencias(aPagar: Map<String, BigDecimal>, pagado: Map<String, BigDecimal>): List<Transferencia> {
        val personas = (aPagar.keys + pagado.keys).toList()
        // saldo > 0: le deben; saldo < 0: debe
        val saldos = personas.associateWith { p ->
            (pagado[p] ?: BigDecimal.ZERO) - (aPagar[p] ?: BigDecimal.ZERO)
        }.toMutableMap()

        val resultado = mutableListOf<Transferencia>()
        while (true) {
            val deudor = saldos.filterValues { it.signum() < 0 }.minByOrNull { it.value } ?: break
            val acreedor = saldos.filterValues { it.signum() > 0 }.maxByOrNull { it.value } ?: break
            val monto = deudor.value.negate().min(acreedor.value)
            resultado.add(Transferencia(deudor.key, acreedor.key, monto))
            saldos[deudor.key] = deudor.value + monto
            saldos[acreedor.key] = acreedor.value - monto
        }
        return resultado
    }

    /** Caso típico: una persona pagó toda la cuenta. */
    fun pagoUnico(aPagar: Map<String, BigDecimal>, quienPago: String): List<Transferencia> =
        transferencias(aPagar, mapOf(quienPago to aPagar.values.fold(BigDecimal.ZERO, BigDecimal::add)))
}
