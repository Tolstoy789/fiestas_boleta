package app.divisorcuentas.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Carga la foto reducida para mandársela a Gemma: el modelo trabaja con imágenes
 * pequeñas y una foto de 50 MP del S25 solo gastaría memoria.
 * ImageDecoder ya aplica la rotación de la cámara.
 */
suspend fun cargarFotoReducida(context: Context, uri: Uri, ladoMaximo: Int = 1024): Bitmap =
    withContext(Dispatchers.IO) {
        val fuente = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(fuente) { decoder, info, _ ->
            val lado = max(info.size.width, info.size.height)
            if (lado > ladoMaximo) {
                val escala = ladoMaximo.toFloat() / lado
                decoder.setTargetSize((info.size.width * escala).toInt(), (info.size.height * escala).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // MediaPipe necesita un bitmap en memoria normal
        }
    }
