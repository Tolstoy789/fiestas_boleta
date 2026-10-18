package app.divisorcuentas.core

import java.math.BigDecimal

/**
 * Convierte el texto de la boleta en una lista de [Item].
 *
 * El camino principal es pedirle a Gemma un JSON ([prompt] + [desdeJson]).
 * Si el modelo no responde algo usable, [desdeTexto] intenta leer los ítems
 * directamente del texto del OCR con expresiones regulares.
 */
object ExtraccionItems {

    /** Prompt para Gemma. Pide solo JSON, sin texto alrededor. */
    fun prompt(textoBoleta: String): String = """
        Eres un asistente que lee boletas de restaurante. A partir del texto de la boleta,
        devuelve SOLO un arreglo JSON con los productos consumidos, sin explicaciones ni markdown.

        Formato exacto:
        [{"item": "nombre del producto", "cantidad": 1, "precio": 1000}]

        Reglas:
        - "precio" es el precio UNITARIO, como número sin símbolo de moneda ni separador de miles.
        - Si la línea dice "2 x Cerveza 7.000", entonces cantidad 2 y precio 3500.
        - No incluyas subtotal, total, IVA, impuestos, propina, descuentos ni datos del local.

        Texto de la boleta:
        $textoBoleta
    """.trimIndent()

    /**
     * Prompt para cuando el modelo también recibe la foto (Gemma 3n entiende imágenes).
     * El texto del OCR va como apoyo, porque a veces lee bien números que en la foto
     * se ven borrosos, y al revés.
     */
    fun promptConImagen(textoBoleta: String): String = """
        Eres un asistente que lee boletas de restaurante. Mira la foto de la boleta adjunta
        y devuelve SOLO un arreglo JSON con los productos consumidos, sin explicaciones ni markdown.

        Formato exacto:
        [{"item": "nombre del producto", "cantidad": 1, "precio": 1000}]

        Reglas:
        - "precio" es el precio UNITARIO, como número sin símbolo de moneda ni separador de miles.
        - Si la línea dice "2 x Cerveza 7.000", entonces cantidad 2 y precio 3500.
        - No incluyas subtotal, total, IVA, impuestos, propina, descuentos ni datos del local.
        - Si la foto y el texto de apoyo no coinciden, confía en la foto.

        Texto de apoyo leído por OCR (puede tener errores).
        Texto de la boleta:
        $textoBoleta
    """.trimIndent()

    /**
     * Lee el JSON que devolvió el modelo. Tolera texto alrededor, bloques ```json,
     * comillas simples y precios escritos como texto ("12.000").
     */
    fun desdeJson(respuesta: String): List<Item> =
        Regex("""\{[^{}]*\}""").findAll(respuesta).mapNotNull { m ->
            val obj = m.value
            val nombre = campoTexto(obj, "item") ?: campoTexto(obj, "nombre") ?: return@mapNotNull null
            val precio = campoValor(obj, "precio")?.let(Precios::parsear) ?: return@mapNotNull null
            val cantidad = campoValor(obj, "cantidad")?.toBigDecimalOrNull()?.toInt()?.takeIf { it > 0 } ?: 1
            Item(nombre.trim(), precio, cantidad)
        }.toList()

    private val PALABRAS_NO_ITEM = Regex(
        """\b(sub\s*total|total|iva|impuesto|propina|neto|descuento|vuelto|efectivo|tarjeta|cambio|rut|boleta|fecha|mesa|garz[oó]n)\b""",
        RegexOption.IGNORE_CASE,
    )

    // [cantidad] [x] nombre ... precio  — ej. "2 x Cerveza   7.000" o "Pizza familiar $12.000"
    private val LINEA_ITEM = Regex(
        """^\s*(?:(\d{1,2})\s*[xX*]?\s+)?(.*?[A-Za-zÁÉÍÓÚÑáéíóúñ].*?)\s+\$?\s*(\d[\d.,]*)\s*$""",
    )

    /** Respaldo sin modelo: busca líneas con "nombre ... precio". El precio leído es el total de la línea. */
    fun desdeTexto(textoBoleta: String): List<Item> =
        textoBoleta.lines().mapNotNull { linea ->
            if (PALABRAS_NO_ITEM.containsMatchIn(linea)) return@mapNotNull null
            val m = LINEA_ITEM.matchEntire(linea) ?: return@mapNotNull null
            val (cant, nombre, precioTxt) = m.destructured
            val totalLinea = Precios.parsear(precioTxt) ?: return@mapNotNull null
            val cantidad = cant.toIntOrNull()?.takeIf { it > 0 } ?: 1
            Item(nombre.trim(), totalLinea.divide(cantidad.toBigDecimal(), java.math.MathContext.DECIMAL64), cantidad)
        }

    // "TOTAL  21.000", "Total a pagar: $21.000"; no "SUBTOTAL" ni "TOTAL IVA"
    private val LINEA_TOTAL = Regex(
        """^\s*total\b(?!\s*(iva|neto|impuesto))[^\d]*(\d[\d.,]*)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * El total impreso en la boleta, si se puede leer. Si hay varias líneas TOTAL
     * se toma la última, que suele ser la final (con propina o descuentos).
     */
    fun totalImpreso(textoBoleta: String): BigDecimal? =
        textoBoleta.lines().mapNotNull { LINEA_TOTAL.matchEntire(it)?.groupValues?.get(2) }
            .lastOrNull()?.let(Precios::parsear)

    /** Usa el JSON del modelo; si no trae ítems, cae al respaldo por texto. */
    fun extraer(respuestaModelo: String?, textoBoleta: String): List<Item> =
        respuestaModelo?.let(::desdeJson)?.takeIf { it.isNotEmpty() } ?: desdeTexto(textoBoleta)

    private fun campoTexto(obj: String, clave: String): String? =
        Regex("""["']$clave["']\s*:\s*["']((?:[^"'\\]|\\.)*)["']""").find(obj)?.groupValues?.get(1)

    private fun campoValor(obj: String, clave: String): String? =
        Regex("""["']$clave["']\s*:\s*["']?(\d(?:[\d.,]*\d)?)""").find(obj)?.groupValues?.get(1)
}

/** Lectura de precios en formatos latinoamericanos y anglosajones. */
object Precios {

    /**
     * "12.000" -> 12000, "12,50" -> 12.50, "1.234,56" -> 1234.56, "1,234.56" -> 1234.56,
     * "3500" -> 3500. Un único separador seguido de exactamente 3 dígitos se toma como miles.
     */
    fun parsear(texto: String): BigDecimal? {
        val s = texto.trim().removePrefix("$").trim().trimEnd('.', ',')
        if (s.isEmpty() || !s.all { it.isDigit() || it == '.' || it == ',' }) return null
        val ultimoPunto = s.lastIndexOf('.')
        val ultimaComa = s.lastIndexOf(',')
        val normal = when {
            ultimoPunto >= 0 && ultimaComa >= 0 -> {
                val decimal = if (ultimoPunto > ultimaComa) '.' else ','
                val miles = if (decimal == '.') ',' else '.'
                s.replace(miles.toString(), "").replace(decimal, '.')
            }
            ultimoPunto >= 0 || ultimaComa >= 0 -> {
                val sep = if (ultimoPunto >= 0) '.' else ','
                val partes = s.split(sep)
                val sonMiles = partes.size > 2 || partes.last().length == 3
                if (sonMiles) s.replace(sep.toString(), "") else s.replace(sep, '.')
            }
            else -> s
        }
        return normal.toBigDecimalOrNull()
    }
}
