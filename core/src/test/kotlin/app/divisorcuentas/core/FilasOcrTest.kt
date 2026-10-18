package app.divisorcuentas.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FilasOcrTest {

    private fun linea(texto: String, x: Int, y: Int, alto: Int = 20) =
        LineaOcr(texto, x, y, x + 100, y + alto)

    @Test
    fun emparejaNombreYPrecioDeColumnasSeparadas() {
        // Llegan como columnas: primero los nombres, luego los precios.
        val lineas = listOf(
            linea("Pizza familiar", 10, 100),
            linea("Cerveza x2", 10, 130),
            linea("12.000", 300, 102),
            linea("7.000", 300, 128),
        )
        assertEquals("Pizza familiar   12.000\nCerveza x2   7.000", FilasOcr.aTexto(lineas))
    }

    @Test
    fun ignoraLineasVaciasYOrdenaDeArribaAbajo() {
        val lineas = listOf(
            linea("TOTAL   19.000", 10, 300),
            linea("  ", 10, 200),
            linea("RESTAURANTE EL SOL", 10, 20),
        )
        assertEquals("RESTAURANTE EL SOL\nTOTAL   19.000", FilasOcr.aTexto(lineas))
    }

    @Test
    fun boletaInclinadaLeveSigueAgrupando() {
        val lineas = listOf(
            linea("Bebida", 10, 100),
            linea("2.000", 300, 108), // 8 px más abajo, dentro de media altura
        )
        assertEquals(1, FilasOcr.agrupar(lineas).size)
    }
}
