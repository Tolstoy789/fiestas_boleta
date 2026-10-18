package app.divisorcuentas.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda el archivo del modelo dentro de la app.
 *
 * AI Edge Gallery guarda sus modelos en su propia carpeta privada, así que otra
 * app no puede leerlos. El usuario elige el `.task` descargado (por ejemplo en
 * Descargas) y se copia una sola vez a `filesDir/modelos/`.
 */
object ModeloLocal {

    private fun carpeta(context: Context) = File(context.filesDir, "modelos").apply { mkdirs() }

    /** El modelo ya importado, si existe. */
    fun actual(context: Context): File? =
        carpeta(context).listFiles { f -> f.extension in setOf("task", "litertlm") }?.maxByOrNull { it.lastModified() }

    /** Copia el modelo elegido por el usuario. Puede tardar: pesa varios GB. */
    suspend fun importar(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val nombre = nombreArchivo(context, uri) ?: "modelo.task"
        carpeta(context).listFiles()?.forEach { it.delete() } // un modelo a la vez
        val destino = File(carpeta(context), nombre)
        context.contentResolver.openInputStream(uri).use { entrada ->
            requireNotNull(entrada) { "No se pudo abrir el archivo del modelo." }
            destino.outputStream().use { entrada.copyTo(it, bufferSize = 1 shl 20) }
        }
        destino
    }

    private fun nombreArchivo(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
