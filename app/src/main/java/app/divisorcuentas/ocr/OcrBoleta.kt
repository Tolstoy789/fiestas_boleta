package app.divisorcuentas.ocr

import android.content.Context
import android.net.Uri
import app.divisorcuentas.core.FilasOcr
import app.divisorcuentas.core.LineaOcr
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Extrae el texto de la foto de una boleta con ML Kit, en el teléfono y sin internet.
 *
 * Se usa el modelo latino "bundled" (incluido en el APK), así que funciona desde
 * la primera vez sin descargar nada de Google Play Services.
 */
class OcrBoleta(private val context: Context) {

    private val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * @param imagen Uri de la foto (cámara o galería).
     * @return Texto en crudo, una fila de la boleta por línea (ítem y precio juntos).
     */
    suspend fun extraerTexto(imagen: Uri): String {
        // Decodificar la foto (12 MP o más) toma tiempo: fuera del hilo de la pantalla
        val entrada = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, imagen) } // respeta la rotación EXIF
        val resultado = reconocedor.process(entrada).await()

        val lineas = resultado.textBlocks
            .flatMap { it.lines }
            .mapNotNull { linea ->
                val caja = linea.boundingBox ?: return@mapNotNull null
                LineaOcr(linea.text, caja.left, caja.top, caja.right, caja.bottom)
            }
        return FilasOcr.aTexto(lineas)
    }

    fun cerrar() = reconocedor.close()
}
