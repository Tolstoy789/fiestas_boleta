package app.divisorcuentas.llm

import android.content.Context
import android.graphics.Bitmap
import app.divisorcuentas.core.ExtraccionItems
import app.divisorcuentas.core.Item
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Punto de conexión con el modelo de lenguaje local.
 *
 * La app solo conoce esta interfaz. Para cambiar de motor (otro modelo, otro SDK
 * de AI Edge) basta con escribir otra implementación de [generar].
 */
interface MotorLlm {
    val nombre: String

    /** true si el motor puede mirar la foto además del texto. */
    val entiendeImagenes: Boolean get() = false

    /** Envía el prompt (y la foto, si el motor la entiende) y devuelve la respuesta en texto. */
    suspend fun generar(prompt: String, imagen: Bitmap? = null): String

    fun cerrar() {}
}

/** El motor que sabe abrir [archivo]: LiteRT-LM para `.litertlm` (Gemma 4) y AI Edge para `.task` (Gemma 3n). */
fun motorPara(context: Context, archivo: File): MotorLlm =
    if (archivo.extension.equals("litertlm", ignoreCase = true)) MotorLiteRtLm(archivo)
    else MotorAiEdge(context, archivo)

/** Ítems leídos y si salieron del modelo (false = el modelo falló y se usó el respaldo por texto). */
data class Extraccion(val items: List<Item>, val delModelo: Boolean)

/**
 * Pide a [motor] los ítems de la boleta. Si el motor entiende imágenes y hay foto,
 * se la manda junto al texto del OCR. Si el modelo falla, usa el respaldo por texto.
 */
suspend fun extraerItems(motor: MotorLlm, textoBoleta: String, foto: Bitmap? = null): Extraccion {
    val conFoto = foto != null && motor.entiendeImagenes
    val prompt = if (conFoto) ExtraccionItems.promptConImagen(textoBoleta) else ExtraccionItems.prompt(textoBoleta)
    val respuesta = runCatching { motor.generar(prompt, foto.takeIf { conFoto }) }.getOrNull()
    val delModelo = respuesta?.let(ExtraccionItems::desdeJson).orEmpty()
    return if (delModelo.isNotEmpty()) Extraccion(delModelo, true)
    else Extraccion(ExtraccionItems.desdeTexto(textoBoleta), false)
}

/**
 * Motor de prueba que no necesita modelo: lee los ítems con expresiones regulares
 * y los devuelve con el mismo formato JSON que debería producir Gemma.
 */
class MotorMock : MotorLlm {
    override val nombre = "Mock (sin modelo)"

    override suspend fun generar(prompt: String, imagen: Bitmap?): String {
        val texto = prompt.substringAfter("Texto de la boleta:").trim()
        return ExtraccionItems.desdeTexto(texto).joinToString(",", "[", "]") {
            """{"item": "${it.nombre.replace("\"", "'")}", "cantidad": ${it.cantidad}, "precio": ${it.precio.toPlainString()}}"""
        }
    }
}

/**
 * Gemma en el teléfono con Google AI Edge (MediaPipe LLM Inference).
 *
 * Lee los modelos `.task`, por ejemplo `gemma-3n-E2B-it-int4.task` de
 * huggingface.co/google (los `.litertlm` van con [MotorLiteRtLm]). El archivo se
 * copia a la carpeta privada de la app con [ModeloLocal.importar].
 *
 * Se intenta cargar con visión (Gemma 3n puede leer la foto) y en la GPU del
 * S25 Ultra; si el modelo no trae visión o la GPU falla, se cae a texto y a CPU.
 */
class MotorAiEdge(context: Context, archivoModelo: File, maxTokens: Int = 2048) : MotorLlm {
    override val nombre = "Gemma (AI Edge): ${archivoModelo.name}"

    private val carga = cargar(context, archivoModelo, maxTokens)
    private val inferencia: LlmInference = carga.first
    private var vision: Boolean = carga.second

    override val entiendeImagenes: Boolean get() = vision

    override suspend fun generar(prompt: String, imagen: Bitmap?): String = withContext(Dispatchers.Default) {
        if (imagen == null || !vision) return@withContext inferencia.generateResponse(prompt)
        try {
            generarConImagen(prompt, imagen)
        } catch (e: Exception) {
            // El modelo no aceptó la imagen: no se vuelve a intentar con fotos.
            vision = false
            inferencia.generateResponse(prompt)
        }
    }

    private fun generarConImagen(prompt: String, imagen: Bitmap): String {
        val opciones = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.2f)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        val sesion = LlmInferenceSession.createFromOptions(inferencia, opciones)
        try {
            sesion.addQueryChunk(prompt)
            sesion.addImage(BitmapImageBuilder(imagen).build())
            return sesion.generateResponse()
        } finally {
            sesion.close()
        }
    }

    override fun cerrar() = inferencia.close()

    private companion object {
        /** @return el motor cargado y si quedó con visión. */
        fun cargar(context: Context, archivo: File, maxTokens: Int): Pair<LlmInference, Boolean> {
            fun crear(backend: LlmInference.Backend, conVision: Boolean): LlmInference {
                val opciones = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(archivo.absolutePath)
                    .setMaxTokens(maxTokens)
                    .setPreferredBackend(backend)
                    .apply { if (conVision) setMaxNumImages(1) }
                    .build()
                return LlmInference.createFromOptions(context, opciones)
            }
            val intentos = listOf(
                LlmInference.Backend.GPU to true,
                LlmInference.Backend.CPU to true,
                LlmInference.Backend.GPU to false,
                LlmInference.Backend.CPU to false,
            )
            var ultimoError: Exception? = null
            for ((backend, conVision) in intentos) {
                try {
                    return crear(backend, conVision) to conVision
                } catch (e: Exception) {
                    ultimoError = e
                }
            }
            throw IllegalStateException("No se pudo cargar el modelo: ${ultimoError?.message}", ultimoError)
        }
    }
}
