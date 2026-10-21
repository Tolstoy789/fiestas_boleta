package app.divisorcuentas.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Las boletas electrónicas que llegan después por correo suelen venir en PDF.
 * ML Kit lee imágenes, así que la primera página se dibuja como JPG en la caché
 * (`cache/boleta-pdf.jpg`) y desde ahí se lee como cualquier foto.
 *
 * @param ladoMaximo Lado mayor de la imagen, en píxeles: suficiente para que el OCR lea letra chica.
 */
suspend fun pdfAImagen(context: Context, pdf: Uri, ladoMaximo: Int = 2400): File = withContext(Dispatchers.IO) {
    val descriptor = context.contentResolver.openFileDescriptor(pdf, "r")
        ?: throw IllegalStateException("No se pudo abrir el PDF.")
    descriptor.use { fd ->
        PdfRenderer(fd).use { lector ->
            if (lector.pageCount == 0) throw IllegalStateException("El PDF no tiene páginas.")
            lector.openPage(0).use { pagina ->
                val escala = ladoMaximo.toFloat() / max(pagina.width, pagina.height)
                val imagen = Bitmap.createBitmap(
                    (pagina.width * escala).toInt().coerceAtLeast(1),
                    (pagina.height * escala).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                imagen.eraseColor(Color.WHITE) // el PDF puede tener fondo transparente
                pagina.render(imagen, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val destino = File(context.cacheDir, "boleta-pdf.jpg")
                destino.outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                imagen.recycle()
                destino
            }
        }
    }
}
