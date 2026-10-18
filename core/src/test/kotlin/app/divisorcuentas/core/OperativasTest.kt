package app.divisorcuentas.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OperativasTest {

    private fun bd(s: String) = BigDecimal(s)
    private fun total(r: Map<String, Detalle>) = r.values.fold(BigDecimal.ZERO) { a, d -> a + d.total }
    private val tres = listOf("Ana", "Beto", "Caro")

    @Test
    fun invitadoNoPagaYElRestoLoCubre() {
        // 30.000 entre 3 + 10 % = 11.000 c/u; Caro es invitada: Ana y Beto ponen 16.500
        val r = DivisorCuenta.dividirIgual(
            listOf(Item("Cena", bd("30000"))), tres, propinaPct = bd("10"),
            ajustes = Ajustes(invitados = setOf("Caro")),
        )
        assertEquals(0, r.getValue("Caro").total.signum())
        assertTrue(r.getValue("Caro").invitado)
        assertEquals(0, bd("16500").compareTo(r.getValue("Ana").total))
        assertEquals(0, bd("33000").compareTo(total(r)))
    }

    @Test
    fun noPuedenSerTodosInvitados() {
        assertFailsWith<IllegalArgumentException> {
            DivisorCuenta.dividirIgual(listOf(Item("x", bd("100"))), listOf("Ana"), ajustes = Ajustes(invitados = setOf("Ana")))
        }
    }

    @Test
    fun descuentoProporcionalAntesDeLaPropina() {
        // Ana consume 30.000 y Beto 10.000; cupón de 4.000 -> Ana 3.000, Beto 1.000
        val r = DivisorCuenta.dividir(
            listOf(Item("A", bd("30000")), Item("B", bd("10000"))),
            mapOf(0 to listOf("Ana"), 1 to listOf("Beto")), listOf("Ana", "Beto"),
            propinaPct = bd("10"), ajustes = Ajustes(descuento = bd("4000")),
        )
        assertEquals(0, bd("29700").compareTo(r.getValue("Ana").total)) // (30000-3000)*1.1
        assertEquals(0, bd("9900").compareTo(r.getValue("Beto").total)) // (10000-1000)*1.1
    }

    @Test
    fun sinPropinaPorPersona() {
        val r = DivisorCuenta.dividirIgual(
            listOf(Item("Cena", bd("20000"))), listOf("Ana", "Beto"), propinaPct = bd("10"),
            ajustes = Ajustes(sinPropina = setOf("Beto")),
        )
        assertEquals(0, bd("11000").compareTo(r.getValue("Ana").total))
        assertEquals(0, bd("10000").compareTo(r.getValue("Beto").total))
    }

    @Test
    fun juntadaDeDosBoletasConPagadoresDistintos() {
        // Bar 9.000 (3.000 c/u) lo pagó Ana; comida 30.000 (10.000 c/u) la pagó Beto.
        val bar = BoletaJuntada("Bar", tres.associateWith { bd("3000") }, "Ana")
        val comida = BoletaJuntada("Comida", tres.associateWith { bd("10000") }, "Beto")
        val t = Juntada.transferencias(listOf(bar, comida))
        // Saldos: Ana +9000-13000 = -4000, Beto +30000-13000 = +17000, Caro -13000
        assertEquals(listOf(Transferencia("Caro", "Beto", bd("13000")), Transferencia("Ana", "Beto", bd("4000"))), t)
    }

    @Test
    fun mensajeDeCobroConDatos() {
        val m = Mensajes.cobro(
            Transferencia("Beto", "Elias", bd("13470")), "Pizzería",
            DatosPago(banco = "BancoEstado", numero = "123"), { "$" + it.toPlainString() },
        )
        assertEquals("Hola Beto! De \"Pizzería\" te toca transferirle $13470 a Elias.\n\nDatos para depositar:\nBanco: BancoEstado\nN° de cuenta: 123\n\nGracias!", m)
    }

    @Test
    fun pendientesYMarcarPagado() {
        val c = CuentaGuardada(
            creada = 1, titulo = "x", items = emptyList(), participantes = tres, pagador = "Ana",
            totales = mapOf("Ana" to "100", "Beto" to "100", "Caro" to "100"),
        )
        var a = Historial.agregarCuenta(ArchivoHistorial(), c)
        assertEquals(setOf("Beto", "Caro"), a.cuentas.single().pendientes().map { it.de }.toSet())
        a = Historial.marcarPagado(a, 1, "Beto", true)
        assertEquals(listOf("Caro"), a.cuentas.single().pendientes().map { it.de })
        a = Historial.marcarPagado(a, 1, "Beto", false)
        assertEquals(2, a.cuentas.single().pendientes().size)
    }

    @Test
    fun respaldoSeCombinaSinPerderNada() {
        val actual = ArchivoHistorial(
            cuentas = listOf(CuentaGuardada(2, "Nueva", emptyList(), tres)),
            grupos = listOf(Grupo("Pega", tres)),
        )
        val respaldo = ArchivoHistorial(
            cuentas = listOf(CuentaGuardada(1, "Vieja", emptyList(), tres)),
            grupos = listOf(Grupo("pega", listOf("Otro")), Grupo("Familia", listOf("Mamá"))),
            datosPago = mapOf("Ana" to DatosPago(banco = "Banco")),
        )
        val r = Historial.combinar(actual, respaldo)
        assertEquals(listOf(2L, 1L), r.cuentas.map { it.creada })
        assertEquals(listOf("Familia", "Pega"), r.grupos.map { it.nombre })
        assertEquals(tres, r.grupos.last().miembros)
        assertEquals("Banco", Historial.datosPagoDe(r, "ana")?.banco)
    }
}
