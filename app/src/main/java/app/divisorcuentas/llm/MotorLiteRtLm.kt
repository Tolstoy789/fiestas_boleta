package app.divisorcuentas.llm

import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Gemma en el teléfono con LiteRT-LM, el motor que reemplaza a MediaPipe LLM Inference.
 *
 * Lee los modelos `.litertlm`, por ejemplo `gemma-4-E4B-it.litertlm` de
 * huggingface.co/litert-community (Gemma 4, sin licencia que aceptar).
 *
 * Igual que [MotorAiEdge]: se intenta con visión y en la GPU; si el modelo no trae
 * visión o la GPU falla, se cae a texto y a CPU.
 *
 * [maxTokens] cuenta la foto, el prompt y la respuesta juntos; 4096 es lo que usa
 * AI Edge Gallery para Gemma 4.
 */
class MotorLiteRtLm(archivoModelo: File, maxTokens: Int = 4096) : MotorLlm {
    override val nombre = "Gemma (LiteRT-LM): ${archivoModelo.name}"

    private val carga = cargar(archivoModelo, maxTokens)
    private val motor: Engine = carga.first
    private var vision: Boolean = carga.second

    override val entiendeImagenes: Boolean get() = vision

    override suspend fun generar(prompt: String, imagen: Bitmap?): String = withContext(Dispatchers.Default) {
        if (imagen == null || !vision) return@withContext responder(Contents.of(prompt))
        try {
            // La foto va antes del texto, como en AI Edge Gallery
            responder(Contents.of(Content.ImageBytes(imagen.comoPng()), Content.Text(prompt)))
        } catch (e: Exception) {
            // El modelo no aceptó la imagen: no se vuelve a intentar con fotos.
            vision = false
            responder(Contents.of(prompt))
        }
    }

    /** Cada boleta va en una conversación nueva, para que no arrastre la anterior. */
    private fun responder(contenido: Contents): String =
        motor.createConversation(
            ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)),
        ).use { it.sendMessage(contenido).toString() }

    // close() falla si el motor ya se cerró
    override fun cerrar() {
        if (motor.isInitialized()) motor.close()
    }

    private companion object {
        fun Bitmap.comoPng(): ByteArray =
            ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        /** @return el motor cargado y si quedó con visión. */
        fun cargar(archivo: File, maxTokens: Int): Pair<Engine, Boolean> {
            fun crear(gpu: Boolean, conVision: Boolean): Engine {
                val motor = Engine(
                    EngineConfig(
                        modelPath = archivo.absolutePath,
                        backend = if (gpu) Backend.GPU() else Backend.CPU(),
                        // La parte que mira la foto corre en la GPU, como pide Gemma
                        visionBackend = if (conVision) Backend.GPU() else null,
                        maxNumTokens = maxTokens,
                    ),
                )
                motor.initialize() // si falla, no queda nada que cerrar
                return motor
            }
            val intentos = listOf(true to true, false to true, true to false, false to false)
            var ultimoError: Exception? = null
            for ((gpu, conVision) in intentos) {
                try {
                    return crear(gpu, conVision) to conVision
                } catch (e: Exception) {
                    ultimoError = e
                }
            }
            throw IllegalStateException("No se pudo cargar el modelo: ${ultimoError?.message}", ultimoError)
        }
    }
}
