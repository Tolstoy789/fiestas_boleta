package app.divisorcuentas.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Lógica pura para dividir una boleta entre participantes.
 *
 * No depende de Android, del OCR ni del modelo: recibe ítems, asignaciones y
 * porcentajes, y devuelve cuánto paga cada persona.
 *
 * Reglas:
 *  - Un ítem asignado a varias personas se divide en partes iguales entre ellas.
 *  - Un ítem sin asignar se reparte entre todos.
 *  - La propina y el impuesto se reparten en proporción al subtotal de cada uno.
 *  - Si el impuesto ya viene incluido en los precios (caso típico del IVA), se
 *    usa `impuestoIncluido = true` y no se suma nada.
 *  - Los montos se redondean a `unidad` y el residuo de redondeo se asigna a
 *    quienes más consumieron, para que la suma cuadre exactamente con el total.
 */

data class Item(
    val nombre: String,
    val precio: BigDecimal,
    val cantidad: Int = 1,
) {
    val total: BigDecimal get() = precio * cantidad.toBigDecimal()
}

/** Resultado por participante. */
data class Detalle(
    val nombre: String,
    val items: List<Pair<String, BigDecimal>>,
    val subtotal: BigDecimal,
    val propina: BigDecimal,
    val impuesto: BigDecimal,
    val total: BigDecimal,
)

object DivisorCuenta {

    private val MC = MathContext.DECIMAL128
    private val CIEN = BigDecimal(100)

    /**
     * Calcula lo que debe pagar cada participante.
     *
     * @param items Ítems de la boleta.
     * @param asignaciones Índice del ítem -> participantes que lo consumieron.
     * @param participantes Nombres de todos los participantes, en orden.
     * @param propinaPct Porcentaje de propina sobre el subtotal (10 = 10 %).
     * @param impuestoPct Porcentaje de impuesto; solo se suma si no viene incluido.
     * @param impuestoIncluido true si los precios ya traen el impuesto.
     * @param unidad Unidad mínima de redondeo (1 para pesos, 0.01 para centavos).
     * @return Mapa nombre -> Detalle, en el orden de `participantes`.
     */
    fun dividir(
        items: List<Item>,
        asignaciones: Map<Int, List<String>>,
        participantes: List<String>,
        propinaPct: BigDecimal = BigDecimal.TEN,
        impuestoPct: BigDecimal = BigDecimal.ZERO,
        impuestoIncluido: Boolean = true,
        unidad: BigDecimal = BigDecimal.ONE,
    ): Map<String, Detalle> {
        require(participantes.isNotEmpty()) { "Debe haber al menos un participante." }
        val desconocidos = asignaciones.values.flatten().toSet() - participantes.toSet()
        require(desconocidos.isEmpty()) { "Participantes no registrados: ${desconocidos.sorted()}" }

        val impuesto = if (impuestoIncluido) BigDecimal.ZERO else impuestoPct

        // 1. Subtotales exactos (sin redondear todavía).
        val consumos = participantes.associateWith { mutableListOf<Pair<String, BigDecimal>>() }
        items.forEachIndexed { i, item ->
            val quienes = asignaciones[i].orEmpty().ifEmpty { participantes }
            val parte = item.total.divide(quienes.size.toBigDecimal(), MC)
            quienes.forEach { consumos.getValue(it).add(item.nombre to parte) }
        }

        // 2. Propina e impuesto proporcionales al subtotal.
        val exactos = participantes.map { p ->
            val lineas = consumos.getValue(p)
            val subtotal = lineas.fold(BigDecimal.ZERO) { acc, l -> acc + l.second }
            val propina = subtotal * propinaPct.divide(CIEN, MC)
            val imp = subtotal * impuesto.divide(CIEN, MC)
            Detalle(p, lineas.toList(), subtotal, propina, imp, subtotal + propina + imp)
        }

        // 3. Redondeo que cuadra con el gran total.
        return redondearCuadrando(exactos, unidad).associateBy { it.nombre }
    }

    private fun redondearCuadrando(detalles: List<Detalle>, unidad: BigDecimal): List<Detalle> {
        fun enUnidades(x: BigDecimal, modo: RoundingMode) =
            x.divide(unidad, MC).setScale(0, modo)

        val exacto = detalles.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
        val objetivo = enUnidades(exacto, RoundingMode.HALF_EVEN)
        val bajos = detalles.associate { it.nombre to enUnidades(it.total, RoundingMode.DOWN) }
        val residuo = (objetivo - bajos.values.fold(BigDecimal.ZERO, BigDecimal::add)).toInt()
        val reciben = detalles.sortedByDescending { it.subtotal }.take(residuo).map { it.nombre }.toSet()

        return detalles.map { d ->
            val unidades = bajos.getValue(d.nombre) + if (d.nombre in reciben) BigDecimal.ONE else BigDecimal.ZERO
            d.copy(total = (unidades * unidad).setScale(unidad.scale()))
        }
    }

    fun totalBoleta(items: List<Item>): BigDecimal =
        items.fold(BigDecimal.ZERO) { acc, it -> acc + it.total }
}
