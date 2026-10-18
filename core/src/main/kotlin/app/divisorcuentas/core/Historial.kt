package app.divisorcuentas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Historial de cuentas y grupos de amigos frecuentes, guardados como un JSON.
 * La app decide dónde se guarda el archivo; aquí solo está el formato.
 */

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
)

@Serializable
data class Grupo(val nombre: String, val miembros: List<String>)

@Serializable
data class ArchivoHistorial(
    val cuentas: List<CuentaGuardada> = emptyList(),
    val grupos: List<Grupo> = emptyList(),
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
}
