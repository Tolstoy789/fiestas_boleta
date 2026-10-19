package app.divisorcuentas.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RobustezTest {

    private fun assertMonto(esperado: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(real), "esperado $esperado, real $real")

    @Test
    fun rutConDigitoVerificador() {
        assertTrue(Rut.esValido("12.345.678-5"))
        assertTrue(Rut.esValido("123456785"))
        assertTrue(Rut.esValido("6000000-k"))
        assertFalse(Rut.esValido("12.345.678-4"))
        assertFalse(Rut.esValido("abc"))
        assertEquals("12.345.678-5", Rut.formatear("123456785"))
        assertEquals("6.000.000-K", Rut.formatear(" 6000000k "))
        assertEquals("12.345.678-4", Rut.formatear("12.345.678-4")) // inválido: queda como vino
    }

    @Test
    fun preciosYPorcentajesComoSeEscribenEnChile() {
        assertMonto("12000", Precios.parsear("$12.000.-"))
        assertMonto("12000", Precios.parsear("12.000-"))
        assertMonto("10", Precios.porcentaje("10%"))
        assertMonto("12.5", Precios.porcentaje("12,5 %"))
        assertNull(Precios.porcentaje("diez"))
    }

    @Test
    fun nombresConApostrofesComillasYAcentosEscapados() {
        val r = ExtraccionItems.desdeJson(
            """[{"item": "Papas O'Higgins", "precio": 5000}, {"item": "Lomo \"a lo pobre\"", "precio": 9000},
               {'item': 'Pisco sour', 'precio': 4500}, {"item": "Café", "precio": 1500}]""",
        )
        assertEquals(listOf("Papas O'Higgins", "Lomo \"a lo pobre\"", "Pisco sour", "Café"), r.map { it.nombre })
    }

    @Test
    fun columnaDePrecioUnitarioSeSacaDelNombre() {
        val r = ExtraccionItems.desdeTexto(
            """
            2 CERVEZA 3.500 7.000
            PISCO SOUR 4.500 9.000
            Coca Cola 350 1.050
            Vino 2015 12.000
            """.trimIndent(),
        )
        assertEquals(listOf("CERVEZA", "PISCO SOUR", "Coca Cola 350", "Vino 2015"), r.map { it.nombre })
        assertEquals(listOf(2, 2, 1, 1), r.map { it.cantidad })
        assertMonto("3500", r[0].precio)
        assertMonto("4500", r[1].precio)
        assertMonto("29050", DivisorCuenta.totalBoleta(r))
    }

    @Test
    fun totalImpresoNoTomaElTotalConPropinaNiElDeItems() {
        val texto = """
            Pizza   12.000
            Total ítems: 5
            TOTAL   $21.000.-
            Total con propina   23.100
        """.trimIndent()
        assertMonto("21000", ExtraccionItems.totalImpreso(texto))
    }

    @Test
    fun alLlegarAlLimiteNoSePierdenDeudasPendientes() {
        val conDeuda = CuentaGuardada(
            creada = 1, titulo = "Vieja con deuda", items = emptyList(), participantes = listOf("Ana", "Beto"),
            pagador = "Ana", totales = mapOf("Ana" to "5000", "Beto" to "5000"),
        )
        var archivo = Historial.agregarCuenta(ArchivoHistorial(), conDeuda)
        for (i in 2L..(Historial.MAX_CUENTAS + 1L)) {
            archivo = Historial.agregarCuenta(
                archivo, CuentaGuardada(creada = i, titulo = "c$i", items = emptyList(), participantes = listOf("Ana")),
            )
        }
        assertEquals(Historial.MAX_CUENTAS, archivo.cuentas.size)
        assertTrue(archivo.cuentas.any { it.creada == 1L }, "la cuenta con deuda se conserva")
        assertTrue(archivo.cuentas.none { it.creada == 2L }, "se va la más antigua sin deudas")
    }

    @Test
    fun borradorIdaYVuelta() {
        val juntada = BoletaJuntada("El bar", mapOf("Ana" to BigDecimal("8000"), "Beto" to BigDecimal("4000")), "Ana")
        val b = Borrador(
            guardado = 1_000L, pantalla = "RESUMEN",
            cuenta = CuentaGuardada(
                creada = 1_000L, titulo = "", items = listOf(ItemGuardado("Pizza", 1, "12000")),
                participantes = listOf("Ana", "Beto"), descuento = "2000", invitados = listOf("Beto"),
            ),
            juntada = listOf(juntada.guardada()),
            totalImpreso = "12000",
        )
        val leido = Borrador.desdeJson(Borrador.aJson(b))
        assertEquals(b, leido)
        assertEquals(juntada.totales.keys, leido!!.juntada.single().boleta().totales.keys)
        assertMonto("8000", leido.juntada.single().boleta().totales["Ana"])
        assertTrue(b.vigente(1_000L + 60_000L))
        assertFalse(b.vigente(1_000L + Borrador.VIGENCIA_MS))
        assertNull(Borrador.desdeJson("{roto"))
    }
}
