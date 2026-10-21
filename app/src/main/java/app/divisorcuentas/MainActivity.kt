package app.divisorcuentas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.divisorcuentas.ui.AppDivisor
import app.divisorcuentas.ui.CuentaViewModel
import app.divisorcuentas.ui.IntroBoleta
import app.divisorcuentas.ui.TemaBoleta

class MainActivity : ComponentActivity() {

    private val vm: CuentaViewModel by viewModels()

    private val preferencias by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Arranque del sistema (ícono sobre papel) y luego la animación propia.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Si se volvió hace poco (por ejemplo después de mandar un cobro por WhatsApp), sin animación
        val volviendo = System.currentTimeMillis() - preferencias.getLong(ULTIMA_VEZ, 0L) < VOLVER_SIN_INTRO_MS
        setContent {
            TemaBoleta {
                var intro by rememberSaveable { mutableStateOf(!volviendo) }
                // La animación se funde con la primera pantalla en vez de cortarse de golpe
                Crossfade(intro, animationSpec = tween(450), label = "inicio") { mostrar ->
                    if (mostrar) IntroBoleta(alTerminar = { intro = false }) else AppDivisor(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Android puede cerrar la app en segundo plano: la cuenta en curso queda guardada
        vm.guardarBorrador()
        preferencias.edit().putLong(ULTIMA_VEZ, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val ULTIMA_VEZ = "ultimaVez"
        const val VOLVER_SIN_INTRO_MS = 30 * 60 * 1000L
    }
}
