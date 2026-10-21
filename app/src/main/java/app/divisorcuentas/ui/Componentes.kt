package app.divisorcuentas.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

// ---------- Personas ----------

/**
 * Un color para cada persona, distinto entre ellas mientras sean 8 o menos. Cada una
 * parte del color que le toca por su nombre, así suele tener el mismo en todas las cuentas.
 */
fun coloresPersonas(personas: List<String>): Map<String, Color> {
    val paleta = Boleta.Personas
    val usados = mutableSetOf<Int>()
    return personas.associateWith { nombre ->
        if (usados.size == paleta.size) usados.clear() // más de 8: los colores se repiten
        val preferido = colorPorNombre(nombre)
        val i = (0 until paleta.size).map { (preferido + it) % paleta.size }.first { it !in usados }
        usados += i
        paleta[i]
    }
}

private fun colorPorNombre(nombre: String) = Math.floorMod(nombre.trim().lowercase().hashCode(), Boleta.Personas.size)

/** Los colores de las personas de la cuenta en pantalla. */
val LocalColoresPersona = compositionLocalOf<Map<String, Color>> { emptyMap() }

@Composable
@ReadOnlyComposable
fun colorDe(nombre: String): Color = LocalColoresPersona.current[nombre] ?: Boleta.Personas[colorPorNombre(nombre)]

/** "Juan Pablo" -> "JP", "ana" -> "A". */
fun iniciales(nombre: String): String = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
    .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase() }
    .ifEmpty { "?" }

/** Círculo con las iniciales: claro con letra de color, o lleno cuando la persona está elegida. */
@Composable
fun Avatar(
    nombre: String,
    modifier: Modifier = Modifier,
    tamano: Dp = 32.dp,
    color: Color = colorDe(nombre),
    lleno: Boolean = false,
) {
    val fondo by animateColorAsState(if (lleno) color else color.copy(alpha = 0.14f).compositeOver(Boleta.Tarjeta), label = "avatar")
    val letra by animateColorAsState(if (lleno) Color.White else color, label = "iniciales")
    Box(modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Text(iniciales(nombre), style = estiloIniciales((tamano.value * 0.4f).sp), color = letra, maxLines = 1)
    }
}

/** Varias personas en una fila de círculos montados, con "+n" si no caben. */
@Composable
fun PilaAvatares(nombres: List<String>, modifier: Modifier = Modifier, maximo: Int = 4, tamano: Dp = 28.dp) {
    val visibles = nombres.take(if (nombres.size > maximo) maximo - 1 else maximo)
    val resto = nombres.size - visibles.size
    val paso = tamano * 0.68f
    val cantidad = visibles.size + if (resto > 0) 1 else 0
    val colores = remember(nombres) { coloresPersonas(nombres) }
    Box(modifier.size(width = paso * (cantidad - 1).coerceAtLeast(0) + tamano, height = tamano)) {
        visibles.forEachIndexed { i, n ->
            Avatar(
                n,
                Modifier.offset(x = paso * i).border(2.dp, Boleta.Tarjeta, CircleShape),
                tamano = tamano,
                color = colores.getValue(n),
            )
        }
        if (resto > 0) {
            Box(
                Modifier.offset(x = paso * visibles.size).size(tamano).clip(CircleShape)
                    .background(Boleta.Carton).border(2.dp, Boleta.Tarjeta, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$resto", style = estiloIniciales((tamano.value * 0.38f).sp), color = Boleta.Grafito, maxLines = 1)
            }
        }
    }
}

/**
 * Letra para dentro de un círculo. Sin el interlineado del tema, que la empuja hacia
 * abajo: queda centrada a la vista.
 */
private fun estiloIniciales(tamano: TextUnit) = TextStyle(fontFamily = FuenteSans, fontWeight = FontWeight.SemiBold, fontSize = tamano)

// ---------- Interacción ----------

/** Se achica un poco mientras se presiona: el dedo "hunde" el botón. */
fun Modifier.escalaAlPresionar(interaccion: InteractionSource, escala: Float = 0.96f): Modifier = composed {
    val presionado by interaccion.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (presionado) escala else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "presion",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Toque con achique y la onda de Material. */
private fun Modifier.tocable(
    habilitado: Boolean = true,
    rol: Role = Role.Button,
    etiqueta: String? = null,
    escala: Float = 0.96f,
    alTocar: () -> Unit,
): Modifier = composed {
    val interaccion = remember { MutableInteractionSource() }
    escalaAlPresionar(interaccion, escala)
        .clickable(
            interactionSource = interaccion,
            indication = LocalIndication.current,
            enabled = habilitado,
            onClickLabel = etiqueta,
            role = rol,
            onClick = alTocar,
        )
}

// ---------- Botones ----------

/** El botón de la acción principal de la pantalla: una píldora de tinta. */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
    fondo: Color = Boleta.Tinta,
) {
    val color by animateColorAsState(if (habilitado) fondo else Boleta.Hilo, label = "fondo")
    val contenido by animateColorAsState(if (habilitado) Boleta.Papel else Boleta.Grafito, label = "contenido")
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(color)
            .tocable(habilitado, alTocar = alTocar)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto, color = contenido, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        if (icono != null) {
            Spacer(Modifier.width(10.dp))
            Icon(icono, contentDescription = null, Modifier.size(20.dp), tint = contenido)
        }
    }
}

/** Botón de una acción secundaria: píldora blanca con borde fino. */
@Composable
fun BotonSecundario(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
    alto: Dp = 48.dp,
    color: Color = Boleta.Tinta,
) {
    val contenido = if (habilitado) color else Boleta.Grafito.copy(alpha = 0.6f)
    Row(
        modifier
            .heightIn(min = alto)
            .clip(CircleShape)
            .background(Boleta.Tarjeta)
            .border(1.dp, Boleta.Borde, CircleShape)
            .tocable(habilitado, alTocar = alTocar)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, Modifier.size(19.dp), tint = contenido)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            texto, color = contenido, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Acción liviana dentro de una tarjeta: texto verde con ícono opcional. */
@Composable
fun BotonTexto(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    color: Color = Boleta.Billete,
    habilitado: Boolean = true,
) {
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .tocable(habilitado, escala = 0.94f, alTocar = alTocar)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (habilitado) color else Boleta.Grafito
        if (icono != null) {
            Icon(icono, contentDescription = null, Modifier.size(18.dp), tint = c)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, color = c, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1)
    }
}

/** Botón redondo con un ícono (atrás, borrar, guardar…). */
@Composable
fun BotonIcono(
    icono: ImageVector,
    descripcion: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Dp = 44.dp,
    tinte: Color = Boleta.Tinta,
    fondo: Color = Color.Transparent,
    borde: Color? = Boleta.Hilo,
    habilitado: Boolean = true,
) {
    Box(
        modifier
            .size(tamano)
            .clip(CircleShape)
            .background(fondo)
            .then(if (borde != null) Modifier.border(1.dp, borde, CircleShape) else Modifier)
            .tocable(habilitado, etiqueta = descripcion, escala = 0.9f, alTocar = alTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, Modifier.size(tamano * 0.46f), tint = if (habilitado) tinte else Boleta.Grafito)
    }
}

// ---------- Tarjetas y estructura ----------

/** Tarjeta blanca de bordes redondos; si recibe [alTocar], se puede tocar entera. */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    alTocar: (() -> Unit)? = null,
    fondo: Color = Boleta.Tarjeta,
    borde: Color? = Boleta.Hilo,
    forma: Shape = RoundedCornerShape(20.dp),
    relleno: PaddingValues = PaddingValues(16.dp),
    etiqueta: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(forma)
            .background(fondo)
            .then(if (borde != null) Modifier.border(1.dp, borde, forma) else Modifier)
            .then(if (alTocar != null) Modifier.tocable(etiqueta = etiqueta, escala = 0.98f, alTocar = alTocar) else Modifier)
            .padding(relleno),
        content = contenido,
    )
}

/** Título chico de una sección, en mayúsculas de máquina. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titulo.uppercase(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
            color = Boleta.Grafito,
        )
        accion?.invoke()
    }
}

@Composable
fun Separador(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Boleta.Hilo))
}

/**
 * Parte de arriba de cada pantalla: volver, en qué paso va la cuenta, el título y
 * una línea que explica qué hacer.
 */
@Composable
fun Encabezado(
    titulo: String,
    subtitulo: String?,
    alVolver: (() -> Unit)?,
    modifier: Modifier = Modifier,
    paso: Pair<Int, Int>? = null,
    accion: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
        if (alVolver != null || paso != null || accion != null) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (alVolver != null) {
                    BotonIcono(Iconos.Atras, "Volver", alVolver, tamano = 44.dp, fondo = Boleta.Tarjeta)
                    Spacer(Modifier.width(14.dp))
                }
                if (paso != null) BarraPasos(paso.first, paso.second)
                Spacer(Modifier.weight(1f))
                accion?.invoke()
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(titulo, style = MaterialTheme.typography.headlineMedium, color = Boleta.Tinta)
        if (subtitulo != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = Boleta.Grafito)
        }
    }
}

/** "Paso 2 de 4" con una barra por paso; la del paso actual se llena al entrar. */
@Composable
fun BarraPasos(actual: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "PASO $actual DE $total",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
            color = Boleta.Grafito,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 1..total) {
                val lleno = remember { Animatable(if (i < actual) 1f else 0f) }
                LaunchedEffect(actual) {
                    lleno.animateTo(if (i <= actual) 1f else 0f, tween(if (i == actual) 650 else 250, easing = FastOutSlowInEasing))
                }
                Box(Modifier.width(26.dp).height(5.dp).clip(CircleShape).background(Boleta.Borde)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(lleno.value).clip(CircleShape)
                            .background(if (i == actual) Boleta.Billete else Boleta.Tinta),
                    )
                }
            }
        }
    }
}

/** Barra fija abajo con la acción principal; sube con el teclado. */
@Composable
fun BarraAccion(modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    val forma = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, forma, ambientColor = Boleta.Tinta.copy(alpha = 0.10f), spotColor = Boleta.Tinta.copy(alpha = 0.16f))
            .clip(forma)
            .background(Boleta.Tarjeta)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido,
    )
}

// ---------- Montos ----------

/**
 * Un monto que corre hasta su valor nuevo cuando cambia (al subir la propina, al
 * marcar a alguien). Con [desdeCero], la primera vez parte de cero.
 */
@Composable
fun MontoAnimado(
    monto: BigDecimal,
    centavos: Boolean,
    modifier: Modifier = Modifier,
    estilo: TextStyle = EstiloMonto,
    desdeCero: Boolean = false,
) {
    val valor = remember { Animatable(if (desdeCero) 0f else monto.toFloat()) }
    LaunchedEffect(monto) { valor.animateTo(monto.toFloat(), tween(if (desdeCero) 700 else 450, easing = FastOutSlowInEasing)) }
    // Mientras corre se muestra el valor aproximado; al llegar, el monto exacto
    val mostrado = if (valor.isRunning) {
        BigDecimal(valor.value.toDouble()).setScale(if (centavos) 2 else 0, RoundingMode.HALF_UP)
    } else {
        monto
    }
    Text(dinero(mostrado, centavos), modifier, style = estilo, maxLines = 1)
}

/**
 * Fila de boleta: etiqueta, puntos y el monto a la derecha. Los puntos corren por detrás
 * de la etiqueta, que los tapa con [fondo] (el color de lo que está debajo).
 */
@Composable
fun FilaMonto(
    etiqueta: String,
    monto: String,
    modifier: Modifier = Modifier,
    fuerte: Boolean = false,
    color: Color = Boleta.Tinta,
    fondo: Color = Boleta.Tarjeta,
) {
    Row(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .weight(1f)
                .alignByBaseline()
                .drawBehind {
                    val y = size.height - 5.dp.toPx()
                    drawLine(
                        Boleta.Borde, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.dp.toPx())),
                    )
                },
        ) {
            Text(
                etiqueta,
                Modifier.background(fondo).padding(end = 8.dp),
                style = if (fuerte) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (fuerte) Boleta.Tinta else Boleta.Grafito,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            monto,
            Modifier.alignByBaseline().padding(start = 8.dp),
            style = EstiloMonto.copy(color = color, fontSize = if (fuerte) 17.sp else 15.sp, fontWeight = if (fuerte) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** Forma de ticket: arriba redondeada y abajo cortada en dientes, como el papel de la impresora. */
class FormaTicket(private val diente: Dp = 14.dp, private val alto: Dp = 7.dp, private val radio: Dp = 20.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { diente.toPx() }
        val h = with(density) { alto.toPx() }
        val r = with(density) { radio.toPx() }
        val dientes = (size.width / d).toInt().coerceAtLeast(1)
        val paso = size.width / dientes
        val camino = Path().apply {
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(size.width - r, 0f)
            arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
            lineTo(size.width, size.height - h)
            for (i in 1..dientes) {
                val x = size.width - paso * i
                lineTo(x + paso / 2, size.height)
                lineTo(x, size.height - h)
            }
            close()
        }
        return Outline.Generic(camino)
    }
}

// ---------- Chips y selección ----------

/** Una persona como chip: su avatar y su nombre. Elegida, se tiñe con su color. */
@Composable
fun ChipPersona(
    nombre: String,
    modifier: Modifier = Modifier,
    elegido: Boolean = false,
    alTocar: (() -> Unit)? = null,
    alQuitar: (() -> Unit)? = null,
) {
    val color = colorDe(nombre)
    val haptico = LocalHapticFeedback.current
    val fondo by animateColorAsState(
        if (elegido) color.copy(alpha = 0.12f).compositeOver(Boleta.Tarjeta) else Boleta.Tarjeta, label = "chip",
    )
    val borde by animateColorAsState(if (elegido) color else Boleta.Hilo, label = "borde")
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(fondo)
            .border(if (elegido) 1.5.dp else 1.dp, borde, CircleShape)
            .then(
                if (alTocar != null) {
                    Modifier.selectable(selected = elegido, role = Role.Checkbox) {
                        haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        alTocar()
                    }
                } else {
                    Modifier
                },
            )
            .padding(start = 5.dp, end = if (alQuitar != null) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nombre, tamano = 30.dp, color = color, lleno = elegido)
        Spacer(Modifier.width(8.dp))
        Text(nombre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Boleta.Tinta, maxLines = 1)
        if (alQuitar != null) {
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = "Quitar a $nombre", role = Role.Button) {
                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    alQuitar()
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Iconos.Cerrar, contentDescription = "Quitar a $nombre", Modifier.size(16.dp), tint = Boleta.Grafito)
            }
        }
    }
}

/**
 * Chip de una opción (propina, tipo de cuenta, un grupo guardado). Elegido se pinta de
 * tinta; con [suave], de verde claro (para "Todos", que es lo normal y no debe pesar).
 */
@Composable
fun ChipOpcion(
    texto: String,
    elegido: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    suave: Boolean = false,
) {
    val haptico = LocalHapticFeedback.current
    val fondo by animateColorAsState(
        when {
            !elegido -> Boleta.Tarjeta
            suave -> Boleta.Menta
            else -> Boleta.Tinta
        },
        label = "opcion",
    )
    val letra by animateColorAsState(
        when {
            !elegido -> Boleta.Tinta
            suave -> Boleta.Billete
            else -> Boleta.Papel
        },
        label = "letra",
    )
    val borde by animateColorAsState(
        when {
            !elegido -> Boleta.Hilo
            suave -> Boleta.Billete.copy(alpha = 0.45f)
            else -> Boleta.Tinta
        },
        label = "borde",
    )
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(fondo)
            .border(1.dp, borde, CircleShape)
            .selectable(selected = elegido, role = Role.RadioButton) {
                haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                alTocar()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, Modifier.size(17.dp), tint = letra)
            Spacer(Modifier.width(7.dp))
        }
        Text(texto, color = letra, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** Dos o más opciones en una píldora, con una "ficha" blanca que se desliza a la elegida. */
@Composable
fun Selector(opciones: List<String>, elegida: Int, alElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptico = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.height(44.dp).clip(CircleShape).background(Boleta.Carton).padding(4.dp),
    ) {
        val ancho = maxWidth / opciones.size
        val x by animateDpAsState(ancho * elegida, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "ficha")
        Box(
            Modifier.offset(x = x).width(ancho).fillMaxHeight()
                .shadow(2.dp, CircleShape).clip(CircleShape).background(Boleta.Tarjeta),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            opciones.forEachIndexed { i, texto ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .selectable(selected = i == elegida, role = Role.RadioButton) {
                            if (i != elegida) haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            alElegir(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        texto,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (i == elegida) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (i == elegida) Boleta.Tinta else Boleta.Grafito,
                    )
                }
            }
        }
    }
}

/** Interruptor con su explicación, en una fila. */
@Composable
fun FilaInterruptor(
    titulo: String,
    marcado: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    habilitado: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .selectable(selected = marcado, enabled = habilitado, role = Role.Switch) { alCambiar(!marcado) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Boleta.Tinta)
            if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = marcado,
            onCheckedChange = null,
            enabled = habilitado,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Boleta.Billete,
                checkedBorderColor = Boleta.Billete,
                uncheckedThumbColor = Boleta.Grafito,
                uncheckedTrackColor = Boleta.Carton,
                uncheckedBorderColor = Boleta.Borde,
            ),
        )
    }
}

/** Círculo que se marca con un visto, con un pequeño rebote. */
@Composable
fun CheckRedondo(marcado: Boolean, alCambiar: (Boolean) -> Unit, descripcion: String, modifier: Modifier = Modifier) {
    val haptico = LocalHapticFeedback.current
    val escala = remember { Animatable(1f) }
    LaunchedEffect(marcado) {
        if (marcado) {
            escala.snapTo(0.6f)
            escala.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    val fondo by animateColorAsState(if (marcado) Boleta.Billete else Color.Transparent, label = "check")
    Box(
        modifier.size(40.dp).clip(CircleShape)
            .semantics { contentDescription = descripcion }
            .toggleable(value = marcado, role = Role.Checkbox) {
                haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                alCambiar(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp)
                .graphicsLayer { scaleX = escala.value; scaleY = escala.value }
                .clip(CircleShape)
                .background(fondo)
                .border(1.6.dp, if (marcado) Boleta.Billete else Boleta.Borde, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (marcado) Icon(Iconos.Check, contentDescription = null, Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/** − cantidad +, con la cantidad editable a mano. */
@Composable
fun Contador(
    texto: String,
    alCambiar: (String) -> Unit,
    descripcion: String,
    modifier: Modifier = Modifier,
    minimo: Int = 1,
    editable: Boolean = true,
) {
    val haptico = LocalHapticFeedback.current
    val valor = texto.toIntOrNull()
    val cambiar = { delta: Int ->
        val nuevo = ((valor ?: minimo) + delta).coerceAtLeast(minimo)
        if (nuevo != valor) haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        alCambiar(nuevo.toString())
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BotonIcono(
            Iconos.Menos, "Una menos: $descripcion", { cambiar(-1) },
            tamano = 34.dp, fondo = Boleta.Tarjeta, borde = Boleta.Borde, habilitado = (valor ?: minimo) > minimo,
        )
        val estilo = EstiloMonto.copy(color = Boleta.Tinta, fontSize = 17.sp, textAlign = TextAlign.Center)
        if (editable) {
            BasicTextField(
                texto, alCambiar,
                Modifier.width(38.dp),
                textStyle = estilo,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Boleta.Tinta),
            )
        } else {
            AnimatedContent(
                targetState = valor ?: minimo,
                transitionSpec = {
                    val sube = targetState > initialState
                    (slideInVertically { if (sube) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (sube) -it else it } + fadeOut())
                },
                label = "contador",
            ) { n -> Text("$n", Modifier.width(38.dp), style = estilo) }
        }
        BotonIcono(Iconos.Mas, "Una más: $descripcion", { cambiar(+1) }, tamano = 34.dp, fondo = Boleta.Tarjeta, borde = Boleta.Borde)
    }
}

/** Barra de avance (cuántos ítems tienen dueño, cuántos pagaron). */
@Composable
fun BarraProgreso(fraccion: Float, modifier: Modifier = Modifier, color: Color = Boleta.Billete) {
    val f by animateFloatAsState(fraccion.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "progreso")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Boleta.Carton)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(color))
    }
}

// ---------- Campos ----------

/** Campo de texto con el estilo de la app. [error] marca el campo y explica qué revisar. */
@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String? = null,
    ejemplo: String? = null,
    error: String? = null,
    ayuda: String? = null,
    hayError: Boolean = error != null,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    acciones: KeyboardActions = KeyboardActions.Default,
    estilo: TextStyle = MaterialTheme.typography.bodyLarge,
    alFinal: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier,
        textStyle = estilo,
        label = etiqueta?.let { { Text(it) } },
        placeholder = ejemplo?.let { { Text(it, color = Boleta.Grafito.copy(alpha = 0.7f)) } },
        trailingIcon = alFinal,
        isError = hayError,
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = teclado,
        keyboardActions = acciones,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Boleta.Tinta,
            unfocusedBorderColor = Boleta.Borde,
            errorBorderColor = Boleta.Timbre,
            focusedContainerColor = Boleta.Tarjeta,
            unfocusedContainerColor = Boleta.Tarjeta,
            errorContainerColor = Boleta.Tarjeta,
            focusedLabelColor = Boleta.Tinta,
            unfocusedLabelColor = Boleta.Grafito,
            errorLabelColor = Boleta.Timbre,
            cursorColor = Boleta.Tinta,
            errorCursorColor = Boleta.Timbre,
            focusedSupportingTextColor = Boleta.Grafito,
            unfocusedSupportingTextColor = Boleta.Grafito,
            errorSupportingTextColor = Boleta.Timbre,
        ),
    )
}

/** Campo chico de un monto o porcentaje, con el signo pegado ("$", "%"). */
@Composable
fun CampoMonto(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    antes: String? = null,
    despues: String? = null,
    hayError: Boolean = false,
    ejemplo: String = "0",
    descripcion: String? = null,
) {
    val borde = if (hayError) Boleta.Timbre else Boleta.Borde
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Boleta.Tarjeta)
            .border(if (hayError) 1.5.dp else 1.dp, borde, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val estilo = EstiloMonto.copy(color = Boleta.Tinta, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (antes != null) Text(antes, style = estilo.copy(color = Boleta.Grafito))
        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (valor.isEmpty()) Text(ejemplo, style = estilo.copy(color = Boleta.Grafito.copy(alpha = 0.5f)))
            BasicTextField(
                valor, alCambiar,
                Modifier.fillMaxWidth().then(
                    if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier,
                ),
                textStyle = estilo,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(Boleta.Tinta),
            )
        }
        if (despues != null) Text(despues, style = estilo.copy(color = Boleta.Grafito))
    }
}

/** Borde de rayitas, para "agregar algo nuevo". */
fun Modifier.bordePunteado(color: Color = Boleta.Borde, radio: Dp = 20.dp): Modifier = drawBehind {
    val r = radio.toPx()
    val grosor = 1.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(grosor / 2, grosor / 2),
        size = Size(size.width - grosor, size.height - grosor),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(grosor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}

// ---------- Estados ----------

/** Cuando todavía no hay nada que mostrar. */
@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(Boleta.Menta), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, Modifier.size(34.dp), tint = Boleta.Billete)
        }
        Spacer(Modifier.height(16.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(detalle, style = MaterialTheme.typography.bodyMedium, color = Boleta.Grafito, textAlign = TextAlign.Center)
    }
}

/** Tipos de aviso que aparecen arriba de la pantalla. */
enum class TipoAviso { BIEN, ERROR, TRABAJANDO, DESHACER }

/** Aviso flotante oscuro: un ícono, el texto y, si corresponde, una acción. */
@Composable
fun Aviso(
    texto: String,
    tipo: TipoAviso,
    modifier: Modifier = Modifier,
    accion: String? = null,
    alAccion: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Boleta.Tinta.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(18.dp))
            .background(Boleta.Tinta)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (tipo) {
                TipoAviso.TRABAJANDO -> CircularProgressIndicator(Modifier.size(20.dp), color = Boleta.Corte, strokeWidth = 2.5.dp)
                TipoAviso.ERROR -> Icon(Iconos.Alerta, contentDescription = null, Modifier.size(22.dp), tint = Color(0xFFFF8A7A))
                TipoAviso.BIEN -> Icon(Iconos.Check, contentDescription = null, Modifier.size(22.dp), tint = Boleta.Corte)
                TipoAviso.DESHACER -> Icon(Iconos.Basura, contentDescription = null, Modifier.size(20.dp), tint = Boleta.Borde)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            texto, Modifier.weight(1f).padding(vertical = 6.dp),
            color = Boleta.Papel, style = MaterialTheme.typography.bodyMedium,
        )
        if (accion != null) {
            Box(
                Modifier.heightIn(min = 40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = alAccion)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(accion, color = Boleta.Corte, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Un aviso que entra desde arriba y se va hacia arriba; mantiene el último texto mientras sale. */
@Composable
fun AvisoAnimado(
    texto: String?,
    tipo: TipoAviso,
    modifier: Modifier = Modifier,
    accion: String? = null,
    alAccion: () -> Unit = {},
) {
    val ultimo = remember { Ref<String>() }
    if (texto != null) ultimo.valor = texto
    AnimatedVisibility(
        visible = texto != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
    ) {
        Aviso(ultimo.valor.orEmpty(), tipo, accion = accion, alAccion = alAccion)
    }
}

/** Guarda un valor sin provocar recomposición: el último texto de un aviso, para que se vea mientras sale. */
private class Ref<T>(var valor: T? = null)

/** Ícono dentro de un círculo de color suave. */
@Composable
fun IconoEnCirculo(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    tamano: Dp = 44.dp,
    fondo: Color = Boleta.Menta,
    tinte: Color = Boleta.Billete,
) {
    Box(modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Icon(icono, contentDescription = null, Modifier.size(tamano * 0.5f), tint = tinte)
    }
}

/** Marca de elegido: un círculo de tinta con un visto, que aparece con un salto. */
@Composable
fun MarcaElegido(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Boleta.Tinta), contentAlignment = Alignment.Center) {
            Icon(Iconos.Check, contentDescription = null, Modifier.size(15.dp), tint = Boleta.Papel)
        }
    }
}

// ---------- Lectura de la boleta ----------

/** Mientras se lee la boleta: un ticket con una línea verde que lo recorre. */
@Composable
fun Escaneando(texto: String, modifier: Modifier = Modifier) {
    val transicion = rememberInfiniteTransition(label = "escaneo")
    val avance by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "linea",
    )
    Tarjeta(modifier.fillMaxWidth(), relleno = PaddingValues(vertical = 28.dp, horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 168.dp, height = 200.dp)) { dibujarEscaneo(avance) }
            Spacer(Modifier.height(20.dp))
            Text(texto, style = MaterialTheme.typography.titleMedium, color = Boleta.Tinta, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "Todo pasa en tu teléfono. Toma unos segundos.",
                style = MaterialTheme.typography.bodySmall, color = Boleta.Grafito, textAlign = TextAlign.Center,
            )
        }
    }
}

private fun DrawScope.dibujarEscaneo(avance: Float) {
    val u = size.width / 168f
    val ancho = 116 * u
    val alto = 168 * u
    val izq = (size.width - ancho) / 2
    val arriba = 16 * u

    // Papel con el borde de abajo dentado
    val dientes = 8
    val paso = ancho / dientes
    val papel = Path().apply {
        moveTo(izq, arriba)
        lineTo(izq + ancho, arriba)
        lineTo(izq + ancho, arriba + alto - 6 * u)
        for (i in 1..dientes) {
            val x = izq + ancho - paso * i
            lineTo(x + paso / 2, arriba + alto)
            lineTo(x, arriba + alto - 6 * u)
        }
        close()
    }
    drawPath(papel, Color.White)
    drawPath(papel, Boleta.Hilo, style = Stroke(1.2f * u))

    fun renglon(x: Float, y: Float, w: Float, h: Float, color: Color) =
        drawRoundRect(color, Offset(izq + x * u, arriba + y * u), Size(w * u, h * u), CornerRadius(1.5f * u))
    renglon(14f, 16f, 50f, 7f, Boleta.Tinta)
    listOf(56f, 44f, 62f, 38f, 50f).forEachIndexed { i, w ->
        renglon(14f, 40f + 18f * i, w, 5f, Boleta.Borde)
        renglon(116f - 14f - 22f, 40f + 18f * i, 22f, 5f, Boleta.Borde)
    }
    renglon(14f, 138f, 34f, 8f, Boleta.Tinta)
    renglon(116f - 14f - 36f, 138f, 36f, 8f, Boleta.Billete)

    // Esquinas del visor
    val margen = 10 * u
    val largo = 18 * u
    val grosor = 3 * u
    val l = izq - margen
    val t = arriba - margen
    val r = izq + ancho + margen
    val b = arriba + alto + margen
    listOf(
        Offset(l, t) to Pair(1f, 1f), Offset(r, t) to Pair(-1f, 1f),
        Offset(l, b) to Pair(1f, -1f), Offset(r, b) to Pair(-1f, -1f),
    ).forEach { (p, s) ->
        drawLine(Boleta.Tinta, p, Offset(p.x + largo * s.first, p.y), grosor, cap = StrokeCap.Round)
        drawLine(Boleta.Tinta, p, Offset(p.x, p.y + largo * s.second), grosor, cap = StrokeCap.Round)
    }

    // Línea de lectura con un brillo suave detrás
    val y = arriba + 6 * u + (alto - 16 * u) * avance
    drawRect(
        Brush.verticalGradient(
            listOf(Boleta.Corte.copy(alpha = 0f), Boleta.Corte.copy(alpha = 0.22f), Boleta.Corte.copy(alpha = 0f)),
            startY = y - 22 * u, endY = y + 22 * u,
        ),
        topLeft = Offset(izq - 4 * u, y - 22 * u),
        size = Size(ancho + 8 * u, 44 * u),
    )
    drawRoundRect(Boleta.Corte, Offset(izq - 8 * u, y - 1.5f * u), Size(ancho + 16 * u, 3 * u), CornerRadius(1.5f * u))
}
