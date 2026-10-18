package app.divisorcuentas.llm

import android.content.Context
import app.divisorcuentas.core.ExtraccionItems
import app.divisorcuentas.core.Item
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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

    /** Envía el prompt al modelo y devuelve su respuesta en texto. */
    suspend fun generar(prompt: String): String

    fun cerrar() {}
}

/** Pide a [motor] los ítems de la boleta y usa el respaldo por texto si falla. */
suspend fun extraerItems(motor: MotorLlm, textoBoleta: String): List<Item> {
    val respuesta = runCatching { motor.generar(ExtraccionItems.prompt(textoBoleta)) }.getOrNull()
    return ExtraccionItems.extraer(respuesta, textoBoleta)
}

/**
 * Motor de prueba que no necesita modelo: lee los ítems con expresiones regulares
 * y los devuelve con el mismo formato JSON que debería producir Gemma.
 */
class MotorMock : MotorLlm {
    override val nombre = "Mock (sin modelo)"

    override suspend fun generar(prompt: String): String {
        val texto = prompt.substringAfter("Texto de la boleta:").trim()
        return ExtraccionItems.desdeTexto(texto).joinToString(",", "[", "]") {
            """{"item": "${it.nombre.replace("\"", "'")}", "cantidad": ${it.cantidad}, "precio": ${it.precio.toPlainString()}}"""
        }
    }
}

/**
 * Gemma en el teléfono con Google AI Edge (MediaPipe LLM Inference).
 *
 * Usa el mismo archivo de modelo que AI Edge Gallery (`.task`), por ejemplo
 * `gemma-3n-E2B-it-int4.task` de huggingface.co/google. El archivo se copia a la
 * carpeta privada de la app con [ModeloLocal.importar].
 *
 * En el S25 Ultra se intenta primero la GPU; si falla se usa la CPU.
 */
class MotorAiEdge(context: Context, archivoModelo: File, maxTokens: Int = 2048) : MotorLlm {
    override val nombre = "Gemma (AI Edge): ${archivoModelo.name}"

    private val inferencia: LlmInference = crear(context, archivoModelo, maxTokens)

    override suspend fun generar(prompt: String): String =
        withContext(Dispatchers.Default) { inferencia.generateResponse(prompt) }

    override fun cerrar() = inferencia.close()

    private companion object {
        fun crear(context: Context, archivo: File, maxTokens: Int): LlmInference {
            fun opciones(backend: LlmInference.Backend) = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(archivo.absolutePath)
                .setMaxTokens(maxTokens)
                .setPreferredBackend(backend)
                .build()
            return runCatching { LlmInference.createFromOptions(context, opciones(LlmInference.Backend.GPU)) }
                .getOrElse { LlmInference.createFromOptions(context, opciones(LlmInference.Backend.CPU)) }
        }
    }
}
