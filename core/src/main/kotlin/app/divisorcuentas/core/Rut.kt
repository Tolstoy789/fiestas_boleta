package app.divisorcuentas.core

/**
 * RUT chileno: revisa el dígito verificador (módulo 11) y lo escribe como
 * 12.345.678-5, que es como lo piden las apps de los bancos.
 */
object Rut {

    /** Solo los dígitos y la K, sin puntos, guion ni espacios. */
    private fun limpiar(rut: String) = rut.uppercase().filter { it.isDigit() || it == 'K' }

    fun esValido(rut: String): Boolean {
        val s = limpiar(rut)
        val cuerpo = s.dropLast(1)
        if (s.length < 2 || cuerpo.any { !it.isDigit() }) return false
        return digitoVerificador(cuerpo) == s.last()
    }

    fun digitoVerificador(cuerpo: String): Char {
        var suma = 0
        var factor = 2
        for (c in cuerpo.reversed()) {
            suma += (c - '0') * factor
            factor = if (factor == 7) 2 else factor + 1
        }
        return when (val r = 11 - suma % 11) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + r
        }
    }

    /** "123456785" -> "12.345.678-5". Si no es un RUT válido, lo deja como vino. */
    fun formatear(rut: String): String {
        if (!esValido(rut)) return rut.trim()
        val s = limpiar(rut)
        val cuerpo = s.dropLast(1).trimStart('0').ifEmpty { "0" }
        val conPuntos = cuerpo.reversed().chunked(3).joinToString(".").reversed()
        return "$conPuntos-${s.last()}"
    }
}
