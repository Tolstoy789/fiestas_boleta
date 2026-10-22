package app.divisorcuentas.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

// ---------- 1. Boleta ----------

@Composable
internal fun PantallaBoleta(vm: CuentaViewModel, volver: () -> Unit) {
    val context = LocalContext.current
    // Saveable: si Android recrea la pantalla mientras la cámara está abierta, no se pierde la foto
    var fotoCamara by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::procesarFoto)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) fotoCamara?.let(vm::procesarFoto)
    }
    val modelo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importarModelo)
    }
    // Boletas que llegan después del evento: una imagen o un PDF en Descargas, Drive, WhatsApp…
    val buscarArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::procesarArchivo)
    }
    val tomarFoto = {
        val archivo = File(context.cacheDir, "boleta.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        fotoCamara = uri
        try {
            camara.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.error = "No se encontró una app de cámara. Elige la foto de la galería."
        }
    }

    val enJuntada = vm.boletasJuntada.isNotEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Encabezado(
            titulo = if (enJuntada) "Boleta ${vm.boletasJuntada.size + 1}" else "La boleta",
            subtitulo = if (enJuntada) "Sigue la juntada: saca la foto de la siguiente boleta."
            else "Sácale una foto o búscala si te la mandaron después.",
            alVolver = volver,
            paso = pasoDe(Pantalla.BOLETA, vm.repartido),
        )
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ocupado = vm.ocupado
            AnimatedContent(
                targetState = ocupado != null,
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                label = "lectura",
            ) { leyendo ->
                if (leyendo) {
                    Escaneando(ocupado ?: "Leyendo la boleta…")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TarjetaFoto(tomarFoto)
                        // Las dos del mismo alto aunque una explicación ocupe más líneas
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaFuente(
                                Iconos.Galeria, "Galería", "Una foto que ya sacaste",
                                Modifier.weight(1f).fillMaxHeight(),
                            ) { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            TarjetaFuente(
                                Iconos.Archivo, "Imagen o PDF", "Descargas, Drive, correo o WhatsApp",
                                Modifier.weight(1f).fillMaxHeight(),
                            ) { buscarArchivo.launch(arrayOf("image/*", "application/pdf")) }
                        }
                        Tarjeta(
                            Modifier.fillMaxWidth(),
                            alTocar = vm::empezarSinFoto,
                            relleno = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            etiqueta = "Escribir los ítems a mano",
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconoEnCirculo(Iconos.Lapiz, tamano = 40.dp, fondo = Boleta.Carton, tinte = Boleta.Tinta)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Escribir los ítems a mano", style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
                                    Text("Sin foto: tú anotas qué se pidió", style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
                                }
                                Icon(Iconos.ChevronDerecha, contentDescription = null, Modifier.size(20.dp), tint = Boleta.Grafito)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TarjetaGemma(vm, importar = { modelo.launch(arrayOf("*/*")) })
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

/** La opción principal: sacar la foto ahora. */
@Composable
private fun TarjetaFoto(alTocar: () -> Unit) {
    Tarjeta(
        Modifier.fillMaxWidth().heightIn(min = 176.dp),
        alTocar = alTocar,
        fondo = Boleta.Tinta,
        borde = null,
        forma = RoundedCornerShape(26.dp),
        relleno = PaddingValues(20.dp),
        etiqueta = "Tomar foto",
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconoEnCirculo(Iconos.Camara, tamano = 56.dp, fondo = Boleta.Corte, tinte = Boleta.Tinta)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "RECOMENDADO",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
                    color = Boleta.Corte,
                )
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
        Text("Tomar foto", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Boleta.Papel)
        Spacer(Modifier.height(2.dp))
        Text(
            "Con buena luz, recta y completa, hasta el total.",
            style = MaterialTheme.typography.bodyMedium, color = Boleta.Papel.copy(alpha = 0.72f),
        )
    }
}

/** Otra forma de traer la boleta: de la galería o de un archivo. */
@Composable
private fun TarjetaFuente(icono: ImageVector, titulo: String, detalle: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Tarjeta(modifier.heightIn(min = 140.dp), alTocar = alTocar, etiqueta = titulo, forma = RoundedCornerShape(22.dp)) {
        IconoEnCirculo(icono, tamano = 44.dp)
        // Arriba, para que los títulos de las dos tarjetas queden a la misma altura
        Spacer(Modifier.height(18.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta)
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Gemma en el teléfono: si se usa, si mira la foto y qué modelo hay. */
@Composable
private fun TarjetaGemma(vm: CuentaViewModel, importar: () -> Unit) {
    val libre = vm.ocupado == null
    Tarjeta(Modifier.fillMaxWidth(), fondo = Boleta.Papel, borde = Boleta.Borde) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoEnCirculo(Iconos.Chispa, tamano = 40.dp, fondo = if (vm.usarGemma) Boleta.Menta else Boleta.Carton,
                tinte = if (vm.usarGemma) Boleta.Billete else Boleta.Grafito)
            Spacer(Modifier.width(12.dp))
            FilaInterruptor(
                "Ordenar con Gemma",
                vm.usarGemma,
                vm::elegirMotor,
                Modifier.weight(1f),
                detalle = "IA en el teléfono, sin internet",
                habilitado = libre,
            )
        }
        AnimatedVisibility(vm.usarGemma, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(8.dp))
                Separador()
                Spacer(Modifier.height(6.dp))
                FilaInterruptor(
                    "Gemma mira la foto",
                    vm.gemmaLeeFoto,
                    { vm.gemmaLeeFoto = it },
                    detalle = "Con Gemma 4 o 3n lee la boleta directo, además del texto. Más lento, pero acierta más.",
                    habilitado = libre,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Separador()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val archivo = vm.archivoModelo
            Column(Modifier.weight(1f)) {
                Text(
                    if (archivo == null) "Sin modelo" else "Modelo",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = Boleta.Grafito,
                )
                Text(
                    archivo?.name ?: "Mientras tanto se usa la lectura simple, sin IA.",
                    style = if (archivo != null) MaterialTheme.typography.labelMedium.copy(color = Boleta.Tinta)
                    else MaterialTheme.typography.bodySmall.copy(color = Boleta.Grafito),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            BotonSecundario(
                if (archivo == null) "Importar modelo" else "Cambiar",
                importar,
                habilitado = libre,
                alto = 40.dp,
                icono = Iconos.Importar.takeIf { archivo == null },
            )
        }
    }
}
