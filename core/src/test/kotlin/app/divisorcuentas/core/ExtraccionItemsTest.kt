package app.divisorcuentas.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtraccionItemsTest {

    private fun assertMonto(esperado: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(real), "esperado $esperado, real $real")

    @Test
    fun precios() {
        assertMonto("12000", Precios.parsear("12.000"))
        assertMonto("12000", Precios.parsear("$12.000"))
        assertMonto("12.50", Precios.parsear("12,50"))
        assertMonto("1234.56", Precios.parsear("1.234,56"))
        assertMonto("1234.56", Precios.parsear("1,234.56"))
        assertMonto("1500000", Precios.parsear("1.500.000"))
        assertMonto("3500", Precios.parsear("3500"))
        assertNull(Precios.parsear("abc"))
    }

    @Test
    fun jsonLimpio() {
        val r = ExtraccionItems.desdeJson("""[{"item": "Pizza", "cantidad": 1, "precio": 12000}]""")
        assertEquals(listOf(Item("Pizza", BigDecimal("12000"), 1)), r)
    }

    @Test
    fun jsonConMarkdownYTextoAlrededor() {
        val respuesta = """
            Claro, aquí está:
            ```json
            [
              {"item": "Cerveza", "cantidad": 2, "precio": "3.500"},
              {"item": "Bebida", "precio": 2000}
            ]
            ```
        """.trimIndent()
        val r = ExtraccionItems.desdeJson(respuesta)
        assertEquals(listOf("Cerveza", "Bebida"), r.map { it.nombre })
        assertEquals(2, r[0].cantidad)
        assertMonto("3500", r[0].precio)
        assertEquals(1, r[1].cantidad)
    }

    @Test
    fun respaldoPorTextoIgnoraTotalesYSeparaCantidad() {
        val texto = """
            RESTAURANTE EL SOL
            Pizza familiar   12.000
            2 x Cerveza   7.000
            Bebida   $2.000
            SUBTOTAL   21.000
            IVA 19%   3.353
            TOTAL   21.000
        """.trimIndent()
        val r = ExtraccionItems.desdeTexto(texto)
        assertEquals(listOf("Pizza familiar", "Cerveza", "Bebida"), r.map { it.nombre })
        assertEquals(2, r[1].cantidad)
        assertMonto("3500", r[1].precio)
        assertMonto("21000", DivisorCuenta.totalBoleta(r))
    }

    @Test
    fun extraerCaeAlRespaldoSiElModeloFalla() {
        val r = ExtraccionItems.extraer("lo siento, no pude leerla", "Pizza   12.000")
        assertEquals(listOf("Pizza"), r.map { it.nombre })
    }

    @Test
    fun totalImpreso() {
        val texto = """
            Pizza   12.000
            SUBTOTAL   21.000
            TOTAL IVA   3.353
            Total a pagar: $21.000
        """.trimIndent()
        assertMonto("21000", ExtraccionItems.totalImpreso(texto))
        assertNull(ExtraccionItems.totalImpreso("Pizza   12.000\nSUBTOTAL 12.000"))
    }
}
