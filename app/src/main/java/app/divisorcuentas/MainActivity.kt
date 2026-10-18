package app.divisorcuentas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Arranque del sistema (ícono sobre papel) y luego la animación propia.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaBoleta {
                var intro by rememberSaveable { mutableStateOf(true) }
                if (intro) IntroBoleta(alTerminar = { intro = false }) else AppDivisor(vm)
            }
        }
    }
}
