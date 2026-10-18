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
    /** Lo que consumió, antes de descuentos. */
    val subtotal: BigDecimal,
    val propina: BigDecimal,
    val impuesto: BigDecimal,
    val total: BigDecimal,
    /** Su parte del descuento de la boleta (se resta). */
    val descuento: BigDecimal = BigDecimal.ZERO,
    /** Lo que pone para cubrir a los invitados. */
    val aporteInvitados: BigDecimal = BigDecimal.ZERO,
    /** true si es invitado: no paga y su parte la cubre el resto. */
    val invitado: Boolean = false,
)

/**
 * Ajustes opcionales del cálculo.
 *
 * @property invitados No pagan (ej. el cumpleañero). Lo suyo, con propina, se
 *   reparte en partes iguales entre los demás.
 * @property sinPropina No ponen propina.
 * @property descuento Monto a restar de la boleta (cupón, promoción), repartido
 *   en proporción a lo que consumió cada uno, antes de calcular la propina.
 */
data class Ajustes(
    val invitados: Set<String> = emptySet(),
    val sinPropina: Set<String> = emptySet(),
    val descuento: BigDecimal = BigDecimal.ZERO,
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
        ajustes: Ajustes = Ajustes(),
    ): Map<String, Detalle> = dividirPorPartes(
        items,
        asignaciones.mapValues { (_, quienes) -> quienes.associateWith { 1 } },
        participantes, propinaPct, impuestoPct, impuestoIncluido, unidad, ajustes,
    )

    /**
     * Igual que [dividir], pero cada participante de un ítem lleva un peso: las
     * unidades que consumió. Con 3 cervezas y `{"Beto": 2, "Ana": 1}`, Beto paga
     * dos tercios. Un ítem sin asignar se reparte en partes iguales entre todos.
     */
    fun dividirPorPartes(
        items: List<Item>,
        partes: Map<Int, Map<String, Int>>,
        participantes: List<String>,
        propinaPct: BigDecimal = BigDecimal.TEN,
        impuestoPct: BigDecimal = BigDecimal.ZERO,
        impuestoIncluido: Boolean = true,
        unidad: BigDecimal = BigDecimal.ONE,
        ajustes: Ajustes = Ajustes(),
    ): Map<String, Detalle> {
        require(participantes.isNotEmpty()) { "Debe haber al menos un participante." }
        val pagan = participantes.filterNot { it in ajustes.invitados }
        require(pagan.isNotEmpty()) { "Alguien tiene que pagar: no pueden ser todos invitados." }
        val desconocidos = partes.values.flatMap { it.keys }.toSet() - participantes.toSet()
        require(desconocidos.isEmpty()) { "Participantes no registrados: ${desconocidos.sorted()}" }

        val impuesto = if (impuestoIncluido) BigDecimal.ZERO else impuestoPct

        // 1. Subtotales exactos (sin redondear todavía).
        val consumos = participantes.associateWith { mutableListOf<Pair<String, BigDecimal>>() }
        items.forEachIndexed { i, item ->
            val pesos = partes[i].orEmpty().filterValues { it > 0 }.ifEmpty { participantes.associateWith { 1 } }
            val totalPesos = pesos.values.sum().toBigDecimal()
            pesos.forEach { (p, peso) ->
                val parte = item.total * peso.toBigDecimal().divide(totalPesos, MC)
                consumos.getValue(p).add(item.nombre to parte)
            }
        }

        // 2. Descuento proporcional al consumo; propina e impuesto sobre lo que queda.
        val subtotales = participantes.associateWith { p -> consumos.getValue(p).fold(BigDecimal.ZERO) { acc, l -> acc + l.second } }
        val totalConsumo = subtotales.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val descuento = ajustes.descuento.max(BigDecimal.ZERO).min(totalConsumo)
        val propios = participantes.map { p ->
            val subtotal = subtotales.getValue(p)
            val desc = if (totalConsumo.signum() == 0) BigDecimal.ZERO else descuento * subtotal.divide(totalConsumo, MC)
            val base = subtotal - desc
            val propina = if (p in ajustes.sinPropina) BigDecimal.ZERO else base * propinaPct.divide(CIEN, MC)
            val imp = base * impuesto.divide(CIEN, MC)
            Detalle(p, consumos.getValue(p).toList(), subtotal, propina, imp, base + propina + imp, descuento = desc)
        }

        // 3. Lo de los invitados se reparte en partes iguales entre quienes pagan.
        val deInvitados = propios.filter { it.nombre in ajustes.invitados }.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
        val aporte = deInvitados.divide(pagan.size.toBigDecimal(), MC)
        val exactos = propios.map { d ->
            if (d.nombre in ajustes.invitados) d.copy(total = BigDecimal.ZERO, invitado = true)
            else d.copy(total = d.total + aporte, aporteInvitados = aporte)
        }

        // 4. Redondeo que cuadra con el gran total.
        return redondearCuadrando(exactos, unidad).associateBy { it.nombre }
    }

    /**
     * Modo "Repartido": el total se divide en partes iguales entre todos, sin
     * mirar quién consumió qué. La propina e impuesto quedan iguales para todos
     * y el redondeo igual cuadra con el total.
     */
    fun dividirIgual(
        items: List<Item>,
        participantes: List<String>,
        propinaPct: BigDecimal = BigDecimal.TEN,
        impuestoPct: BigDecimal = BigDecimal.ZERO,
        impuestoIncluido: Boolean = true,
        unidad: BigDecimal = BigDecimal.ONE,
        ajustes: Ajustes = Ajustes(),
    ): Map<String, Detalle> =
        dividirPorPartes(items, emptyMap(), participantes, propinaPct, impuestoPct, impuestoIncluido, unidad, ajustes)

    private fun redondearCuadrando(detalles: List<Detalle>, unidad: BigDecimal): List<Detalle> {
        fun enUnidades(x: BigDecimal, modo: RoundingMode) =
            x.divide(unidad, MC).setScale(0, modo)

        val exacto = detalles.fold(BigDecimal.ZERO) { acc, d -> acc + d.total }
        val objetivo = enUnidades(exacto, RoundingMode.HALF_EVEN)
        val bajos = detalles.associate { it.nombre to enUnidades(it.total, RoundingMode.DOWN) }
        val residuo = (objetivo - bajos.values.fold(BigDecimal.ZERO, BigDecimal::add)).toInt()
        val reciben = detalles.filterNot { it.invitado }.sortedByDescending { it.subtotal }.take(residuo).map { it.nombre }.toSet()

        return detalles.map { d ->
            val unidades = bajos.getValue(d.nombre) + if (d.nombre in reciben) BigDecimal.ONE else BigDecimal.ZERO
            d.copy(total = (unidades * unidad).setScale(unidad.scale()))
        }
    }

    fun totalBoleta(items: List<Item>): BigDecimal =
        items.fold(BigDecimal.ZERO) { acc, it -> acc + it.total }
}
