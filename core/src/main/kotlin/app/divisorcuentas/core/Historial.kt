package app.divisorcuentas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal

/**
 * Historial de cuentas y grupos de amigos frecuentes, guardados como un JSON.
 * La app decide dónde se guarda el archivo; aquí solo está el formato.
 */

@Serializable
data class TransferenciaGuardada(val de: String, val a: String, val monto: String)

@Serializable
data class ItemGuardado(val nombre: String, val cantidad: Int, val precio: String)

@Serializable
data class CuentaGuardada(
    /** Momento en que se guardó, en milisegundos desde 1970. Sirve también de id. */
    val creada: Long,
    val titulo: String,
    val items: List<ItemGuardado>,
    val participantes: List<String>,
    /** Índice del ítem -> unidades que consumió cada participante. */
    val partes: Map<Int, Map<String, Int>> = emptyMap(),
    val propinaPct: String = "10",
    val impuestoIncluido: Boolean = true,
    val impuestoPct: String = "0",
    val conCentavos: Boolean = false,
    val pagador: String? = null,
    /** Lo que pagó cada uno, ya calculado. */
    val totales: Map<String, String> = emptyMap(),
    /** true si el total se dividió en partes iguales en vez de por consumo. */
    val repartido: Boolean = false,
    val invitados: List<String> = emptyList(),
    val sinPropina: List<String> = emptyList(),
    val descuento: String = "0",
    /** Quién le transfiere a quién (de esta boleta o de toda la juntada). */
    val transferencias: List<TransferenciaGuardada> = emptyList(),
    /** Quienes ya hicieron su transferencia. */
    val pagados: List<String> = emptyList(),
    /**
     * Boleta intermedia de una juntada: sus deudas quedan incluidas en la
     * última boleta de la juntada, que trae el saldo neto de todas.
     */
    val parteDeJuntada: Boolean = false,
) {
    /**
     * Transferencias de la cuenta. Las cuentas guardadas antes de existir este
     * campo se calculan desde quién pagó y los totales.
     */
    fun deudas(): List<Transferencia> {
        if (parteDeJuntada) return emptyList()
        if (transferencias.isNotEmpty()) {
            return transferencias.mapNotNull { t -> t.monto.toBigDecimalOrNull()?.let { Transferencia(t.de, t.a, it) } }
        }
        val quien = pagador ?: return emptyList()
        return Saldos.pagoUnico(totales.mapValues { it.value.toBigDecimalOrNull() ?: BigDecimal.ZERO }, quien)
    }

    /** Transferencias que todavía nadie marcó como hechas. */
    fun pendientes(): List<Transferencia> = deudas().filter { it.de !in pagados }
}

@Serializable
data class Grupo(val nombre: String, val miembros: List<String>)

/** Datos bancarios para que el resto le deposite a quien pagó la cuenta. */
@Serializable
data class DatosPago(
    val titular: String = "",
    val rut: String = "",
    val banco: String = "",
    val tipoCuenta: String = "",
    val numero: String = "",
    val correo: String = "",
) {
    val vacio: Boolean get() = lineas().isEmpty()

    /** Etiqueta y valor de cada dato que no esté en blanco, en el orden en que se escriben al transferir. */
    fun lineas(): List<Pair<String, String>> = listOf(
        "Nombre" to titular, "RUT" to rut, "Banco" to banco,
        "Tipo de cuenta" to tipoCuenta, "N° de cuenta" to numero, "Correo" to correo,
    ).map { (k, v) -> k to v.trim() }.filter { it.second.isNotEmpty() }
}

@Serializable
data class ArchivoHistorial(
    val cuentas: List<CuentaGuardada> = emptyList(),
    val grupos: List<Grupo> = emptyList(),
    /** Persona -> sus datos bancarios, para no escribirlos en cada reunión. */
    val datosPago: Map<String, DatosPago> = emptyMap(),
)

object Historial {

    const val MAX_CUENTAS = 100

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun aJson(archivo: ArchivoHistorial): String = json.encodeToString(archivo)

    /** Lee el archivo; si está vacío o dañado, parte de cero en vez de cerrar la app. */
    fun desdeJson(texto: String?): ArchivoHistorial =
        texto?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ArchivoHistorial>(it) }.getOrNull() }
            ?: ArchivoHistorial()

    /** Agrega la cuenta al comienzo (la más reciente primero) y conserva las últimas [MAX_CUENTAS]. */
    fun agregarCuenta(archivo: ArchivoHistorial, cuenta: CuentaGuardada): ArchivoHistorial =
        archivo.copy(cuentas = (listOf(cuenta) + archivo.cuentas.filter { it.creada != cuenta.creada }).take(MAX_CUENTAS))

    fun borrarCuenta(archivo: ArchivoHistorial, creada: Long): ArchivoHistorial =
        archivo.copy(cuentas = archivo.cuentas.filter { it.creada != creada })

    /** Guarda el grupo; si ya existe uno con el mismo nombre (sin importar mayúsculas), lo reemplaza. */
    fun guardarGrupo(archivo: ArchivoHistorial, grupo: Grupo): ArchivoHistorial {
        val nombre = grupo.nombre.trim()
        val limpio = Grupo(nombre, grupo.miembros.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        val otros = archivo.grupos.filterNot { it.nombre.equals(nombre, ignoreCase = true) }
        return archivo.copy(grupos = (otros + limpio).sortedBy { it.nombre.lowercase() })
    }

    fun borrarGrupo(archivo: ArchivoHistorial, nombre: String): ArchivoHistorial =
        archivo.copy(grupos = archivo.grupos.filterNot { it.nombre == nombre })

    /** Marca (o desmarca) que [persona] ya hizo su transferencia en la cuenta [creada]. */
    fun marcarPagado(archivo: ArchivoHistorial, creada: Long, persona: String, pagado: Boolean): ArchivoHistorial =
        archivo.copy(cuentas = archivo.cuentas.map { c ->
            if (c.creada != creada) c
            else c.copy(pagados = if (pagado) (c.pagados + persona).distinct() else c.pagados - persona)
        })

    /**
     * Junta un respaldo con lo que ya hay: suma las cuentas que falten (si una
     * está en los dos, gana la del respaldo), agrega los grupos y los datos
     * bancarios que no estén.
     */
    fun combinar(actual: ArchivoHistorial, respaldo: ArchivoHistorial): ArchivoHistorial {
        val porId = (actual.cuentas + respaldo.cuentas).associateBy { it.creada }
        var r = actual.copy(cuentas = porId.values.sortedByDescending { it.creada }.take(MAX_CUENTAS))
        respaldo.grupos.filter { g -> actual.grupos.none { it.nombre.equals(g.nombre, ignoreCase = true) } }
            .forEach { r = guardarGrupo(r, it) }
        respaldo.datosPago.filter { (p, _) -> datosPagoDe(actual, p) == null }
            .forEach { (p, d) -> r = guardarDatosPago(r, p, d) }
        return r
    }

    /** Datos bancarios de [persona], sin importar mayúsculas; null si no hay. */
    fun datosPagoDe(archivo: ArchivoHistorial, persona: String): DatosPago? =
        archivo.datosPago.entries.firstOrNull { it.key.equals(persona.trim(), ignoreCase = true) }?.value

    /** Guarda (o borra, si vienen vacíos) los datos bancarios de [persona]. */
    fun guardarDatosPago(archivo: ArchivoHistorial, persona: String, datos: DatosPago): ArchivoHistorial {
        val nombre = persona.trim()
        val otros = archivo.datosPago.filterKeys { !it.equals(nombre, ignoreCase = true) }
        return archivo.copy(datosPago = if (datos.vacio) otros else otros + (nombre to datos))
    }
}
