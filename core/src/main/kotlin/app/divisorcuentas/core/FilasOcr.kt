package app.divisorcuentas.core

/**
 * Reconstruye las filas de una boleta a partir de las líneas que detecta el OCR.
 *
 * ML Kit suele devolver el nombre del ítem y su precio como líneas separadas
 * (columna izquierda y columna derecha). Si se concatenan en el orden en que
 * llegan, el modelo recibe primero todos los nombres y después todos los
 * precios, y no puede emparejarlos. Aquí se agrupan por altura para obtener
 * "Pizza familiar   12.000" en una sola fila.
 */

/** Una línea detectada por el OCR con su caja en píxeles. */
data class LineaOcr(
    val texto: String,
    val izquierda: Int,
    val arriba: Int,
    val derecha: Int,
    val abajo: Int,
) {
    val alto: Int get() = abajo - arriba
    val centroY: Float get() = (arriba + abajo) / 2f
}

object FilasOcr {

    /**
     * Agrupa líneas cuyo centro vertical cae dentro de `tolerancia` veces la
     * altura media de la fila, y ordena cada fila de izquierda a derecha.
     *
     * @return El texto de la boleta, una fila por línea.
     */
    fun aTexto(lineas: List<LineaOcr>, tolerancia: Float = 0.5f): String =
        agrupar(lineas, tolerancia).joinToString("\n") { fila ->
            fila.joinToString("   ") { it.texto.trim() }
        }

    fun agrupar(lineas: List<LineaOcr>, tolerancia: Float = 0.5f): List<List<LineaOcr>> {
        val filas = mutableListOf<MutableList<LineaOcr>>()
        for (linea in lineas.filter { it.texto.isNotBlank() }.sortedBy { it.centroY }) {
            val fila = filas.lastOrNull()
            if (fila != null && mismaFila(fila, linea, tolerancia)) fila.add(linea)
            else filas.add(mutableListOf(linea))
        }
        return filas.map { fila -> fila.sortedBy { it.izquierda } }
    }

    private fun mismaFila(fila: List<LineaOcr>, linea: LineaOcr, tolerancia: Float): Boolean {
        val centro = fila.map { it.centroY }.average().toFloat()
        val alto = fila.map { it.alto }.average().toFloat().coerceAtLeast(1f)
        return kotlin.math.abs(linea.centroY - centro) <= alto * tolerancia
    }
}
