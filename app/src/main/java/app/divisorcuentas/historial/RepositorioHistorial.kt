package app.divisorcuentas.historial

import android.content.Context
import app.divisorcuentas.core.ArchivoHistorial
import app.divisorcuentas.core.Borrador
import app.divisorcuentas.core.Historial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda el historial y los grupos en un archivo privado de la app
 * (`files/historial.json`), y la cuenta en curso en `files/borrador.json`.
 * Escribe primero a un temporal y luego lo renombra, para no dejar el archivo a
 * medias si la app se cierra en mitad de la escritura.
 */
class RepositorioHistorial(context: Context) {

    private val archivo = File(context.filesDir, "historial.json")
    private val borrador = File(context.filesDir, "borrador.json")

    /** Una escritura a la vez y en el orden en que se pidieron: la última versión es la que queda. */
    private val escritura = Mutex()

    suspend fun leer(): ArchivoHistorial = withContext(Dispatchers.IO) {
        Historial.desdeJson(if (archivo.exists()) archivo.readText() else null)
    }

    suspend fun guardar(datos: ArchivoHistorial) = escritura.withLock {
        withContext(Dispatchers.IO) { escribir(archivo, Historial.aJson(datos)) }
    }

    /** Es un archivo chico: se lee al abrir la app, antes de dibujar la primera pantalla. */
    fun leerBorrador(): Borrador? =
        runCatching { if (borrador.exists()) Borrador.desdeJson(borrador.readText()) else null }.getOrNull()

    fun guardarBorrador(b: Borrador) = escribir(borrador, Borrador.aJson(b))

    private fun escribir(destino: File, texto: String) {
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        temporal.writeText(texto)
        if (!temporal.renameTo(destino)) {
            destino.writeText(texto)
            temporal.delete()
        }
    }
}
