package app.divisorcuentas.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import app.divisorcuentas.R
import app.divisorcuentas.core.BoletaJuntada
import app.divisorcuentas.core.DatosPago
import app.divisorcuentas.core.Detalle
import app.divisorcuentas.core.Item
import app.divisorcuentas.core.Juntada
import app.divisorcuentas.core.Transferencia
import app.divisorcuentas.ui.dinero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Todo lo que va en el PDF; se arma desde la pantalla de resumen. */
data class DatosPdf(
    val titulo: String,
    val fecha: Long,
    val items: List<Item>,
    /** Índice del ítem -> unidades por persona. Vacío = entre todos. */
    val partes: Map<Int, Map<String, Int>>,
    val participantes: List<String>,
    val reparto: Map<String, Detalle>,
    /** Modo Repartido: el total en partes iguales, sin detalle por consumo. */
    val repartido: Boolean,
    val propinaPct: BigDecimal,
    /** Porcentaje de impuesto sumado aparte; null si ya viene incluido en los precios. */
    val impuestoPct: BigDecimal?,
    val pagador: String?,
    val transferencias: List<Transferencia>,
    /** Datos bancarios de quienes reciben transferencias, para que el resto les deposite. */
    val datosPorAcreedor: Map<String, DatosPago>,
    /** Todas las boletas de la juntada, contando esta; vacío si es una sola. Las transferencias saldan todas. */
    val juntada: List<BoletaJuntada> = emptyList(),
    val centavos: Boolean,
) {
    val boletasEnJuntada: Int get() = juntada.size.coerceAtLeast(1)
}

/**
 * Genera un PDF tamaño A4 con el detalle de la cuenta, pensado para mandarlo al
 * grupo: primero cuánto paga cada uno y a quién, después qué consumió cada
 * persona y al final la boleta completa con quién compartió cada ítem.
 */
object DetallePdf {

    suspend fun crear(context: Context, datos: DatosPdf): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            Dibujo(context, doc, datos).todo()
            val carpeta = File(context.cacheDir, "pdf").apply { mkdirs() }
            carpeta.listFiles()?.forEach { it.delete() } // solo guardamos el último
            val archivo = File(carpeta, nombreArchivo(datos.titulo))
            archivo.outputStream().use { doc.writeTo(it) }
            archivo
        } finally {
            doc.close()
        }
    }

    fun nombreArchivo(titulo: String): String {
        val limpio = titulo.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40)
        return "Cuenta-${limpio.ifEmpty { "detalle" }}.pdf"
    }
}

private const val ANCHO = 595 // A4 en puntos
private const val ALTO = 842
private const val MARGEN = 40f
private const val PIE = 48f

private val TINTA = 0xFF1B1F24.toInt()
private val PAPEL = 0xFFF6F3EC.toInt()
private val BILLETE = 0xFF1F7A52.toInt()
private val CORTE = 0xFF3FBF83.toInt()
private val GRAFITO = 0xFF5D636B.toInt()
private val BORDE = 0xFFD9D3C5.toInt()
private val VERDE_CLARO = 0xFFE6F2EB.toInt()
private val GRIS_CLARO = 0xFFC9CDD2.toInt()

private class Dibujo(context: Context, private val doc: PdfDocument, private val d: DatosPdf) {

    private val sans = ResourcesCompat.getFont(context, R.font.ibm_plex_sans) ?: Typeface.SANS_SERIF
    private val mono = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular) ?: Typeface.MONOSPACE
    private val monoFuerte = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_semibold) ?: mono
    private val locale = Locale.forLanguageTag("es-CL")

    private var pagina: PdfDocument.Page? = null
    private lateinit var lienzo: Canvas
    private var numero = 0
    private var y = 0f
    private val derecha = ANCHO - MARGEN
    private val anchoUtil = ANCHO - 2 * MARGEN

    private fun texto(tamano: Float, color: Int = TINTA, peso: Int = 400, tipo: Typeface = sans) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tipo
            textSize = tamano
            this.color = color
            // IBM Plex Sans es una fuente variable: el grosor se elige con el eje wght
            if (tipo === sans) fontVariationSettings = "'wght' $peso"
        }

    private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun linea(color: Int = BORDE, grosor: Float = 0.8f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = grosor }

    private fun plata(x: BigDecimal) = dinero(x, d.centavos)

    // ---------- Páginas ----------

    fun todo() {
        nuevaPagina()
        encabezado()
        resumenPorPersona()
        quienPaga()
        juntadaCompleta()
        if (!d.repartido) detallePorPersona()
        boletaCompleta()
        nota()
        cerrarPagina()
    }

    private fun nuevaPagina() {
        cerrarPagina()
        numero++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
        pagina = p
        lienzo = p.canvas
        y = MARGEN
        if (numero > 1) {
            lienzo.drawText("${d.titulo} · continuación", MARGEN, y + 10, texto(10f, GRAFITO))
            y += 28
        }
    }

    private fun cerrarPagina() {
        val p = pagina ?: return
        val pie = texto(9f, GRAFITO)
        lienzo.drawLine(MARGEN, ALTO - PIE + 8, derecha, ALTO - PIE + 8, linea())
        lienzo.drawText("Hecho con Divisor de cuentas", MARGEN, ALTO - PIE + 24, pie)
        alDerecha("Página $numero", derecha, ALTO - PIE + 24, pie)
        doc.finishPage(p)
        pagina = null
    }

    /** Pasa a una página nueva si no caben [alto] puntos más. */
    private fun asegurar(alto: Float) {
        if (y + alto > ALTO - PIE) nuevaPagina()
    }

    // ---------- Secciones ----------

    private fun encabezado() {
        val alto = 118f
        lienzo.drawRoundRect(RectF(MARGEN, y, derecha, y + alto), 10f, 10f, relleno(TINTA))
        val x = MARGEN + 20
        lienzo.drawText("DETALLE DE LA CUENTA", x, y + 28, texto(9.5f, CORTE, 600).apply { letterSpacing = 0.12f })
        lienzo.drawText(recortar(d.titulo, texto(22f, PAPEL, 600), anchoUtil - 220), x, y + 58, texto(22f, PAPEL, 600))
        val fecha = SimpleDateFormat("EEEE d 'de' MMMM yyyy, HH:mm", locale).format(Date(d.fecha))
        lienzo.drawText(fecha.replaceFirstChar { it.uppercase() }, x, y + 80, texto(10.5f, GRIS_CLARO))
        val modo = if (d.repartido) "Repartido en partes iguales" else "Detallado por consumo"
        lienzo.drawText("${d.participantes.size} personas · ${d.items.size} ítems · $modo", x, y + 98, texto(10.5f, GRIS_CLARO))

        val total = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.total }
        alDerecha("TOTAL A PAGAR", derecha - 20, y + 28, texto(9.5f, CORTE, 600).apply { letterSpacing = 0.12f })
        alDerecha(plata(total), derecha - 20, y + 62, texto(26f, PAPEL, tipo = monoFuerte))
        val condiciones = buildString {
            append("Incluye propina ${porcentaje(d.propinaPct)}")
            if (d.impuestoPct != null) append(" e impuesto ${porcentaje(d.impuestoPct)}")
        }
        alDerecha(condiciones, derecha - 20, y + 82, texto(10f, GRIS_CLARO))
        if (d.impuestoPct == null) alDerecha("Precios con IVA incluido", derecha - 20, y + 98, texto(10f, GRIS_CLARO))
        y += alto + 28
    }

    private fun titulo(t: String) {
        asegurar(40f)
        lienzo.drawText(t, MARGEN, y + 14, texto(14f, TINTA, 600))
        y += 26
    }

    private fun resumenPorPersona() {
        titulo("Cuánto paga cada uno")
        val conImpuesto = d.impuestoPct != null
        // Descuento (−) y parte de los invitados (+): sin esta columna la fila no suma el total
        val conAjuste = d.reparto.values.any { it.descuento.signum() != 0 || it.aporteInvitados.signum() != 0 }
        // Columnas: persona | consumo | propina | (impuesto) | (ajuste) | total
        val colTotal = derecha - 12
        var x = colTotal - 104
        val colAjuste = if (conAjuste) x.also { x -= 84 } else 0f
        val colImpuesto = if (conImpuesto) x.also { x -= 84 } else 0f
        val colPropina = x.also { x -= 84 }
        val colConsumo = x
        val fila = 30f

        asegurar(fila * 2)
        val cab = texto(9.5f, GRAFITO, 600)
        lienzo.drawText("PERSONA", MARGEN + 12, y + 14, cab)
        alDerecha("CONSUMO", colConsumo, y + 14, cab)
        alDerecha("PROPINA", colPropina, y + 14, cab)
        if (conImpuesto) alDerecha("IMPUESTO", colImpuesto, y + 14, cab)
        if (conAjuste) alDerecha("AJUSTE", colAjuste, y + 14, cab)
        alDerecha("TOTAL", colTotal, y + 14, cab)
        y += 22

        val maximo = d.reparto.values.maxOfOrNull { it.total }?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        d.reparto.values.forEachIndexed { i, p ->
            asegurar(fila)
            if (i % 2 == 0) lienzo.drawRect(MARGEN, y, derecha, y + fila, relleno(PAPEL))
            // Barra fina proporcional al total, para ver de un vistazo quién paga más
            val largo = (p.total.divide(maximo, MathContext.DECIMAL64).toFloat() * 60f).coerceAtLeast(2f)
            lienzo.drawRoundRect(RectF(MARGEN + 12, y + 21, MARGEN + 12 + largo, y + 24), 1.5f, 1.5f, relleno(BILLETE))
            lienzo.drawText(recortar(p.nombre, texto(12f, TINTA, 500), (colConsumo - MARGEN - 100).coerceAtLeast(60f)), MARGEN + 12, y + 17, texto(12f, TINTA, 500))
            val num = texto(11f, GRAFITO, tipo = mono)
            alDerecha(plata(p.subtotal), colConsumo, y + 18, num)
            alDerecha(plata(p.propina), colPropina, y + 18, num)
            if (conImpuesto) alDerecha(plata(p.impuesto), colImpuesto, y + 18, num)
            if (conAjuste) alDerecha(conSigno(p.aporteInvitados - p.descuento), colAjuste, y + 18, num)
            if (p.invitado) alDerecha("Invitado", colTotal, y + 19, texto(12f, BILLETE, 600))
            else alDerecha(plata(p.total), colTotal, y + 19, texto(13f, TINTA, tipo = monoFuerte))
            y += fila
        }
        asegurar(fila + 4)
        lienzo.drawRect(MARGEN, y, derecha, y + fila + 4, relleno(TINTA))
        lienzo.drawText("Total", MARGEN + 12, y + 20, texto(12f, PAPEL, 600))
        val total = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.total }
        alDerecha(plata(total), colTotal, y + 21, texto(13f, PAPEL, tipo = monoFuerte))
        y += fila + 4
        if (conAjuste) {
            lienzo.drawText("Ajuste: descuento de la boleta (−) y parte de los invitados (+).", MARGEN + 12, y + 14, texto(9f, GRAFITO))
            y += 18
        }
        y += 24
    }

    /** Las boletas de la juntada y, por persona, lo que le tocaba, lo que puso y el saldo. */
    private fun juntadaCompleta() {
        if (d.juntada.size < 2) return
        titulo("La juntada completa")
        d.juntada.forEachIndexed { i, b ->
            asegurar(22f)
            val fila = texto(11f)
            lienzo.drawText(recortar("${i + 1}. ${b.titulo}", fila, anchoUtil - 250), MARGEN + 12, y + 15, fila)
            lienzo.drawText(b.pagador?.let { "pagó $it" } ?: "sin pagador", derecha - 230, y + 15, texto(9.5f, GRAFITO))
            alDerecha(plata(b.totales.values.fold(BigDecimal.ZERO, BigDecimal::add)), derecha - 12, y + 15, texto(11f, tipo = mono))
            y += 22
        }
        y += 10

        val colSaldo = derecha - 12
        val colPuso = colSaldo - 120
        val colToca = colPuso - 110
        asegurar(44f)
        val cab = texto(9.5f, GRAFITO, 600)
        lienzo.drawText("PERSONA", MARGEN + 12, y + 14, cab)
        alDerecha("LE TOCABA", colToca, y + 14, cab)
        alDerecha("PUSO", colPuso, y + 14, cab)
        alDerecha("SALDO", colSaldo, y + 14, cab)
        y += 20
        val toca = Juntada.aPagar(d.juntada)
        val puso = Juntada.pagado(d.juntada)
        (toca.keys + puso.keys).forEachIndexed { i, persona ->
            asegurar(24f)
            if (i % 2 == 0) lienzo.drawRect(MARGEN, y, derecha, y + 24, relleno(PAPEL))
            val t = toca[persona] ?: BigDecimal.ZERO
            val p = puso[persona] ?: BigDecimal.ZERO
            val num = texto(10.5f, GRAFITO, tipo = mono)
            lienzo.drawText(recortar(persona, texto(11f, TINTA, 500), colToca - MARGEN - 100), MARGEN + 12, y + 16, texto(11f, TINTA, 500))
            alDerecha(plata(t), colToca, y + 16, num)
            alDerecha(plata(p), colPuso, y + 16, num)
            val saldo = p - t
            alDerecha(conSigno(saldo), colSaldo, y + 16, texto(11f, if (saldo.signum() > 0) BILLETE else TINTA, tipo = monoFuerte))
            y += 24
        }
        asegurar(18f)
        lienzo.drawText("Saldo +: le deben · Saldo −: debe. Las transferencias de arriba dejan todo en cero.", MARGEN + 12, y + 14, texto(9f, GRAFITO))
        y += 18 + 24
    }

    private fun quienPaga() {
        if (d.transferencias.isEmpty()) return
        val titulo = when {
            d.boletasEnJuntada > 1 -> "Para quedar a mano: saldo de las ${d.boletasEnJuntada} boletas de la juntada"
            d.pagador != null -> "Para quedar a mano: ${d.pagador} pagó la cuenta"
            else -> "Para quedar a mano"
        }
        val acreedores = d.transferencias.map { it.a }.distinct().filter { d.datosPorAcreedor[it]?.vacio == false }
        val altoDatos = acreedores.sumOf { 40 + d.datosPorAcreedor.getValue(it).lineas().size * 18 }.toFloat()
        val alto = 44f + d.transferencias.size * 22f + altoDatos
        asegurar(minOf(alto, ALTO - PIE - MARGEN - 40))
        lienzo.drawRoundRect(RectF(MARGEN, y, derecha, y + alto), 10f, 10f, relleno(VERDE_CLARO))
        lienzo.drawText(recortar(titulo, texto(12.5f, BILLETE, 600), anchoUtil - 32), MARGEN + 16, y + 24, texto(12.5f, BILLETE, 600))
        var fy = y + 48
        d.transferencias.forEach { t ->
            lienzo.drawText("${t.de} le transfiere a ${t.a}", MARGEN + 16, fy, texto(11.5f))
            alDerecha(plata(t.monto), derecha - 16, fy, texto(12f, TINTA, tipo = monoFuerte))
            fy += 22
        }
        acreedores.forEach { quien ->
            // Datos bancarios en un recuadro blanco, para copiarlos tal cual en la app del banco
            val datos = d.datosPorAcreedor.getValue(quien).lineas()
            val altoCaja = 40f + datos.size * 18f
            lienzo.drawRoundRect(RectF(MARGEN + 12, fy - 6, derecha - 12, fy - 6 + altoCaja - 8), 8f, 8f, relleno(0xFFFFFFFF.toInt()))
            lienzo.drawText("Datos para depositar a $quien", MARGEN + 26, fy + 14, texto(10.5f, TINTA, 600))
            var dy = fy + 34
            datos.forEach { (etiqueta, valor) ->
                lienzo.drawText(etiqueta, MARGEN + 26, dy, texto(10f, GRAFITO))
                lienzo.drawText(recortar(valor, texto(11f, TINTA, tipo = mono), anchoUtil - 170), MARGEN + 136, dy, texto(11f, TINTA, tipo = mono))
                dy += 18
            }
            fy += altoCaja
        }
        y += alto + 24
    }

    private fun detallePorPersona() {
        titulo("Qué consumió cada uno")
        d.participantes.forEach { persona ->
            val p = d.reparto[persona] ?: return@forEach
            val lineas = consumosDe(persona)
            val alto = 38f + lineas.size * 18f + 24f
            asegurar(minOf(alto, 120f))
            lienzo.drawText(persona, MARGEN + 12, y + 18, texto(12.5f, TINTA, 600))
            if (p.invitado) alDerecha("Invitado: lo cubre el resto", derecha - 12, y + 18, texto(11f, BILLETE, 600))
            else alDerecha(plata(p.total), derecha - 12, y + 18, texto(13f, TINTA, tipo = monoFuerte))
            y += 30
            lineas.forEach { l ->
                asegurar(18f)
                val nombre = texto(10.5f)
                val detalle = texto(9.5f, GRAFITO)
                val anchoNombre = anchoUtil - 220
                val corto = recortar(l.nombre, nombre, anchoNombre)
                lienzo.drawText(corto, MARGEN + 24, y + 12, nombre)
                lienzo.drawText(l.como, MARGEN + 30 + nombre.measureText(corto), y + 12, detalle)
                alDerecha(plata(l.monto), derecha - 12, y + 12, texto(10.5f, GRAFITO, tipo = mono))
                y += 18
            }
            asegurar(22f)
            val resumen = buildString {
                append("Consumo ${plata(p.subtotal)}")
                if (p.descuento.signum() != 0) append(" − descuento ${plata(p.descuento)}")
                append(" + propina ${plata(p.propina)}")
                if (p.impuesto.signum() != 0) append(" + impuesto ${plata(p.impuesto)}")
                if (p.aporteInvitados.signum() != 0) append(" + invitados ${plata(p.aporteInvitados)}")
            }
            lienzo.drawText(resumen, MARGEN + 24, y + 14, texto(9.5f, GRAFITO))
            y += 24
            lienzo.drawLine(MARGEN, y, derecha, y, linea())
            y += 10
        }
        y += 14
    }

    private fun boletaCompleta() {
        titulo("La boleta completa")
        val colTotal = derecha - 12
        val colQuien = MARGEN + 300
        asegurar(40f)
        val cab = texto(9.5f, GRAFITO, 600)
        lienzo.drawText("CANT.", MARGEN + 12, y + 14, cab)
        lienzo.drawText("ÍTEM", MARGEN + 52, y + 14, cab)
        lienzo.drawText("QUIÉN", colQuien, y + 14, cab)
        alDerecha("TOTAL", colTotal, y + 14, cab)
        y += 20
        lienzo.drawLine(MARGEN, y, derecha, y, linea())
        d.items.forEachIndexed { i, item ->
            asegurar(24f)
            val fila = texto(10.5f)
            lienzo.drawText(item.cantidad.toString(), MARGEN + 12, y + 16, texto(10.5f, tipo = mono))
            lienzo.drawText(recortar(item.nombre, fila, colQuien - MARGEN - 64), MARGEN + 52, y + 16, fila)
            lienzo.drawText(recortar(quienes(i, item), texto(9.5f, GRAFITO), colTotal - colQuien - 80), colQuien, y + 16, texto(9.5f, GRAFITO))
            alDerecha(plata(item.total), colTotal, y + 16, texto(10.5f, tipo = mono))
            y += 24
            lienzo.drawLine(MARGEN, y, derecha, y, linea(BORDE, 0.5f))
        }
        val subtotal = d.items.fold(BigDecimal.ZERO) { a, it -> a + it.total }
        val propina = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.propina }
        val impuesto = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.impuesto }
        val total = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.total }
        y += 6
        val descuento = d.reparto.values.fold(BigDecimal.ZERO) { a, p -> a + p.descuento }
        filaSuma("Suma de los ítems", plata(subtotal), false)
        if (descuento.signum() != 0) filaSuma("Descuento", "−" + plata(descuento), false)
        filaSuma("Propina ${porcentaje(d.propinaPct)}", plata(propina), false)
        if (d.impuestoPct != null) filaSuma("Impuesto ${porcentaje(d.impuestoPct)}", plata(impuesto), false)
        filaSuma("Total a pagar", plata(total), true)
        y += 16
    }

    private fun filaSuma(etiqueta: String, monto: String, fuerte: Boolean) {
        asegurar(22f)
        val izquierda = derecha - 260
        if (fuerte) lienzo.drawLine(izquierda, y + 2, derecha, y + 2, linea(TINTA, 1f))
        lienzo.drawText(etiqueta, izquierda, y + 17, texto(if (fuerte) 12f else 10.5f, if (fuerte) TINTA else GRAFITO, if (fuerte) 600 else 400))
        alDerecha(monto, derecha - 12, y + 17, texto(if (fuerte) 13f else 10.5f, TINTA, tipo = if (fuerte) monoFuerte else mono))
        y += 22
    }

    private fun nota() {
        asegurar(30f)
        val unidad = if (d.centavos) "centavos" else "pesos"
        val pie = texto(8.5f, GRAFITO)
        lienzo.drawText(
            if (d.repartido) "El total se dividió en partes iguales entre quienes pagan."
            else "La propina se reparte en proporción a lo que consumió cada uno.",
            MARGEN, y + 12, pie,
        )
        lienzo.drawText(
            "Los montos se redondean a $unidad y la diferencia se asigna de a uno, para que todo sume el total.",
            MARGEN, y + 24, pie,
        )
    }

    // ---------- Ayudas ----------

    private class Linea(val nombre: String, val como: String, val monto: BigDecimal)

    /** Pesos efectivos de un ítem: los marcados, o todos por igual si no hay nadie marcado. */
    private fun pesos(i: Int): Map<String, Int> =
        d.partes[i].orEmpty().filterValues { it > 0 }.ifEmpty { d.participantes.associateWith { 1 } }

    private fun consumosDe(persona: String): List<Linea> = d.items.mapIndexedNotNull { i, item ->
        val pesos = pesos(i)
        val peso = pesos[persona] ?: return@mapIndexedNotNull null
        val suma = pesos.values.sum()
        val explicito = d.partes[i].orEmpty().any { it.value > 0 }
        val como = when {
            pesos.size == 1 -> "  solo"
            explicito && item.cantidad > 1 -> "  $peso de ${item.cantidad} unid."
            explicito -> "  entre ${pesos.size}"
            else -> "  entre todos"
        }
        val monto = item.total * peso.toBigDecimal().divide(suma.toBigDecimal(), MathContext.DECIMAL128)
        Linea(item.nombre, como, monto)
    }

    private fun quienes(i: Int, item: Item): String {
        val marcados = d.partes[i].orEmpty().filterValues { it > 0 }
        if (marcados.isEmpty()) return "Entre todos"
        return marcados.entries.joinToString(", ") { (p, u) -> if (item.cantidad > 1) "$p ×$u" else p }
    }

    private fun porcentaje(x: BigDecimal) = x.stripTrailingZeros().toPlainString() + " %"

    /** "+$3.500", "−$2.000" o "—" si es cero. */
    private fun conSigno(x: BigDecimal) = when (x.signum()) {
        0 -> "—"
        1 -> "+" + plata(x)
        else -> "−" + plata(x.negate())
    }

    private fun alDerecha(texto: String, x: Float, y: Float, pintura: Paint) {
        lienzo.drawText(texto, x - pintura.measureText(texto), y, pintura)
    }

    /** Corta el texto con "…" si no cabe en [ancho]. */
    private fun recortar(texto: String, pintura: Paint, ancho: Float): String {
        if (pintura.measureText(texto) <= ancho) return texto
        val cabe = pintura.breakText(texto, true, ancho - pintura.measureText("…"), null)
        return texto.take(cabe.coerceAtLeast(1)).trimEnd() + "…"
    }
}
