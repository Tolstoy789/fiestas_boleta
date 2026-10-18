package app.divisorcuentas.historial

import android.content.Context
import app.divisorcuentas.core.ArchivoHistorial
import app.divisorcuentas.core.Historial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda el historial y los grupos en un archivo privado de la app
 * (`files/historial.json`). Escribe primero a un temporal y luego lo renombra,
 * para no dejar el archivo a medias si la app se cierra en mitad de la escritura.
 */
class RepositorioHistorial(context: Context) {

    private val archivo = File(context.filesDir, "historial.json")

    suspend fun leer(): ArchivoHistorial = withContext(Dispatchers.IO) {
        Historial.desdeJson(if (archivo.exists()) archivo.readText() else null)
    }

    suspend fun guardar(datos: ArchivoHistorial) = withContext(Dispatchers.IO) {
        val temporal = File(archivo.parentFile, "historial.json.tmp")
        temporal.writeText(Historial.aJson(datos))
        if (!temporal.renameTo(archivo)) {
            archivo.writeText(temporal.readText())
            temporal.delete()
        }
    }
}
