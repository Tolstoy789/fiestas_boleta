package app.divisorcuentas.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Guarda el archivo del modelo dentro de la app.
 *
 * AI Edge Gallery guarda sus modelos en su propia carpeta privada, así que otra
 * app no puede leerlos. El usuario elige el `.litertlm` o `.task` descargado (por
 * ejemplo en Descargas) y se copia una sola vez a `filesDir/modelos/`.
 */
object ModeloLocal {

    private val EXTENSIONES = setOf("task", "litertlm")

    /** Espacio que se deja libre en el teléfono además de lo que pesa el modelo. */
    private const val RESERVA = 500L * 1024 * 1024

    private fun carpeta(context: Context) = File(context.filesDir, "modelos").apply { mkdirs() }

    /** El modelo ya importado, si existe. */
    fun actual(context: Context): File? =
        carpeta(context).listFiles { f -> f.extension in EXTENSIONES }?.maxByOrNull { it.lastModified() }

    /**
     * Copia el modelo elegido por el usuario. Puede tardar: pesa varios GB.
     * Se copia a un archivo temporal y solo al terminar reemplaza al anterior, así
     * una copia que falla a la mitad no deja la app sin modelo ni con uno roto.
     */
    suspend fun importar(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val (nombreLeido, tamano) = info(context, uri)
        val nombre = nombreLeido ?: "modelo.task"
        require(nombre.substringAfterLast('.', "").lowercase() in EXTENSIONES) {
            "\"$nombre\" no es un modelo. Elige el archivo .litertlm o .task de Gemma (por ejemplo gemma-4-E4B-it.litertlm)."
        }
        val carpeta = carpeta(context)
        if (tamano != null && carpeta.usableSpace < tamano + RESERVA) {
            throw IllegalStateException(
                "No hay espacio: el modelo pesa ${gb(tamano)} y quedan ${gb(carpeta.usableSpace)} libres.",
            )
        }
        val parcial = File(carpeta, "importando.parcial")
        try {
            context.contentResolver.openInputStream(uri).use { entrada ->
                requireNotNull(entrada) { "No se pudo abrir el archivo del modelo." }
                parcial.outputStream().use { entrada.copyTo(it, bufferSize = 1 shl 20) }
            }
            carpeta.listFiles()?.filter { it != parcial }?.forEach { it.delete() } // un modelo a la vez
            val destino = File(carpeta, nombre)
            check(parcial.renameTo(destino)) { "No se pudo guardar el modelo." }
            destino
        } finally {
            parcial.delete()
        }
    }

    private fun gb(bytes: Long) = String.format(Locale.forLanguageTag("es-CL"), "%.1f GB", bytes / 1e9)

    /** Nombre y tamaño del archivo elegido, si el proveedor los entrega. */
    private fun info(context: Context, uri: Uri): Pair<String?, Long?> =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (!c.moveToFirst()) return@use (null to null)
                val nombre = if (c.isNull(0)) null else c.getString(0)
                val tamano = if (c.isNull(1)) null else c.getLong(1)
                nombre to tamano
            } ?: (null to null)
}
