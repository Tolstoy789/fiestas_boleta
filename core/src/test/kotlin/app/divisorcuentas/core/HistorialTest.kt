package app.divisorcuentas.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HistorialTest {

    private fun cuenta(creada: Long) = CuentaGuardada(
        creada = creada,
        titulo = "Cumpleaños",
        items = listOf(ItemGuardado("Pizza", 1, "12000")),
        participantes = listOf("Ana", "Beto"),
        partes = mapOf(0 to mapOf("Ana" to 1, "Beto" to 1)),
        pagador = "Ana",
        totales = mapOf("Ana" to "6600", "Beto" to "6600"),
    )

    @Test
    fun idaYVueltaEnJson() {
        val archivo = Historial.agregarCuenta(ArchivoHistorial(), cuenta(1))
            .let { Historial.guardarGrupo(it, Grupo("Pega", listOf("Ana", "Beto"))) }
        assertEquals(archivo, Historial.desdeJson(Historial.aJson(archivo)))
    }

    @Test
    fun archivoDanadoOVacioPartesDeCero() {
        assertEquals(ArchivoHistorial(), Historial.desdeJson("{no es json"))
        assertEquals(ArchivoHistorial(), Historial.desdeJson(null))
    }

    @Test
    fun laMasRecientePrimeroYConLimite() {
        var a = ArchivoHistorial()
        for (i in 1..(Historial.MAX_CUENTAS + 5)) a = Historial.agregarCuenta(a, cuenta(i.toLong()))
        assertEquals(Historial.MAX_CUENTAS, a.cuentas.size)
        assertEquals((Historial.MAX_CUENTAS + 5).toLong(), a.cuentas.first().creada)
    }

    @Test
    fun grupoConMismoNombreSeReemplaza() {
        var a = Historial.guardarGrupo(ArchivoHistorial(), Grupo("Pega", listOf("Ana")))
        a = Historial.guardarGrupo(a, Grupo("pega ", listOf("Ana", " Beto", "Ana")))
        assertEquals(listOf(Grupo("pega", listOf("Ana", "Beto"))), a.grupos)
    }
}
