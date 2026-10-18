package app.divisorcuentas.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DivisorCuentaTest {

    private fun bd(s: String) = BigDecimal(s)

    /** Compara por valor, ignorando la escala (8800 == 8800.00). */
    private fun assertMonto(esperado: String, real: BigDecimal) =
        assertEquals(0, bd(esperado).compareTo(real), "esperado $esperado, real $real")

    private val itemsEjemplo = listOf(
        Item("Pizza", bd("12000")),
        Item("Cerveza", bd("3500"), cantidad = 2),
        Item("Bebida", bd("2000")),
    )

    @Test
    fun itemCompartidoYPropina() {
        val r = DivisorCuenta.dividir(
            itemsEjemplo,
            mapOf(0 to listOf("Ana", "Beto"), 1 to listOf("Beto"), 2 to listOf("Ana")),
            listOf("Ana", "Beto"),
            propinaPct = bd("10"),
        )
        assertMonto("8000", r.getValue("Ana").subtotal)
        assertMonto("13000", r.getValue("Beto").subtotal)
        assertMonto("8800", r.getValue("Ana").total)
        assertMonto("14300", r.getValue("Beto").total)
    }

    @Test
    fun itemSinAsignarSeReparteEntreTodos() {
        val r = DivisorCuenta.dividir(
            listOf(Item("Pan", bd("3000"))), emptyMap(), listOf("A", "B", "C"),
            propinaPct = BigDecimal.ZERO,
        )
        listOf("A", "B", "C").forEach { assertMonto("1000", r.getValue(it).total) }
    }

    @Test
    fun redondeoCuadraConTotal() {
        val r = DivisorCuenta.dividir(
            listOf(Item("Torta", bd("10000"))), emptyMap(), listOf("A", "B", "C"),
            propinaPct = bd("10"),
        )
        assertMonto("11000", r.values.fold(BigDecimal.ZERO) { acc, d -> acc + d.total })
    }

    @Test
    fun impuestoAparte() {
        val r = DivisorCuenta.dividir(
            listOf(Item("Plato", bd("10.00"))), emptyMap(), listOf("A"),
            propinaPct = bd("15"), impuestoPct = bd("8"),
            impuestoIncluido = false, unidad = bd("0.01"),
        )
        assertMonto("12.30", r.getValue("A").total)
    }

    @Test
    fun participanteDesconocido() {
        assertFailsWith<IllegalArgumentException> {
            DivisorCuenta.dividir(itemsEjemplo, mapOf(0 to listOf("Zoe")), listOf("Ana"))
        }
    }

    @Test
    fun repartoPorUnidades() {
        // 3 cervezas a 3.000: Beto tomó 2 y Ana 1
        val r = DivisorCuenta.dividirPorPartes(
            listOf(Item("Cerveza", bd("3000"), cantidad = 3)),
            mapOf(0 to mapOf("Beto" to 2, "Ana" to 1)),
            listOf("Ana", "Beto"),
            propinaPct = BigDecimal.ZERO,
        )
        assertMonto("3000", r.getValue("Ana").total)
        assertMonto("6000", r.getValue("Beto").total)
    }

    @Test
    fun pesosEnCeroSeIgnoran() {
        val r = DivisorCuenta.dividirPorPartes(
            listOf(Item("Pan", bd("2000"))),
            mapOf(0 to mapOf("Ana" to 0)),
            listOf("Ana", "Beto"),
            propinaPct = BigDecimal.ZERO,
        )
        assertMonto("1000", r.getValue("Ana").total)
        assertMonto("1000", r.getValue("Beto").total)
    }
}
