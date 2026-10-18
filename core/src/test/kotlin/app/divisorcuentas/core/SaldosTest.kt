package app.divisorcuentas.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaldosTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun unaPersonaPagoTodo() {
        val t = Saldos.pagoUnico(mapOf("Ana" to bd("8800"), "Beto" to bd("14300")), quienPago = "Ana")
        assertEquals(listOf(Transferencia("Beto", "Ana", bd("14300"))), t)
    }

    @Test
    fun variosPagaronParte() {
        val aPagar = mapOf("Ana" to bd("10000"), "Beto" to bd("10000"), "Caro" to bd("10000"))
        val pagado = mapOf("Ana" to bd("20000"), "Beto" to bd("10000"))
        assertEquals(listOf(Transferencia("Caro", "Ana", bd("10000"))), Saldos.transferencias(aPagar, pagado))
    }

    @Test
    fun cadaUnoPagoLoSuyoNoHayTransferencias() {
        val montos = mapOf("Ana" to bd("5000"), "Beto" to bd("7000"))
        assertTrue(Saldos.transferencias(montos, montos).isEmpty())
    }

    @Test
    fun lasTransferenciasCuadran() {
        val aPagar = mapOf("A" to bd("3000"), "B" to bd("4000"), "C" to bd("5000"), "D" to bd("6000"))
        val pagado = mapOf("A" to bd("10000"), "B" to bd("8000"))
        val t = Saldos.transferencias(aPagar, pagado)
        val recibe = t.groupBy { it.a }.mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { acc, x -> acc + x.monto } }
        assertEquals(0, bd("7000").compareTo(recibe.getValue("A")))
        assertEquals(0, bd("4000").compareTo(recibe.getValue("B")))
    }
}
