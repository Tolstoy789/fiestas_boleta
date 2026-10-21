package app.divisorcuentas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Íconos de línea propios (24 × 24, trazo redondeado), dibujados para la app:
 * sin depender de una librería de íconos y con el mismo grosor que la letra.
 * Cada texto es un trazado SVG; varios trazados se dibujan juntos.
 */
object Iconos {
    val Atras = icono("atras", "M15 18L9 12L15 6")
    val Siguiente = icono("siguiente", "M5 12H19", "M13 6L19 12L13 18")
    val ChevronDerecha = icono("chevron-derecha", "M9 6L15 12L9 18")
    val ChevronAbajo = icono("chevron-abajo", "M6 9L12 15L18 9")
    val Mas = icono("mas", "M12 5V19", "M5 12H19")
    val Menos = icono("menos", "M6 12H18")
    val Cerrar = icono("cerrar", "M7 7L17 17", "M17 7L7 17")
    val Check = icono("check", "M5 12.5L10 17.5L19 7.5")
    val Camara = icono(
        "camara",
        "M4 8.5C4 7.7 4.7 7 5.5 7H8L9.5 5H14.5L16 7H18.5C19.3 7 20 7.7 20 8.5V17.5C20 18.3 19.3 19 18.5 19H5.5C4.7 19 4 18.3 4 17.5Z",
        "M15.5 13A3.5 3.5 0 1 1 8.5 13A3.5 3.5 0 1 1 15.5 13Z",
    )
    val Galeria = icono(
        "galeria",
        "M4 6.5C4 5.1 5.1 4 6.5 4H17.5C18.9 4 20 5.1 20 6.5V17.5C20 18.9 18.9 20 17.5 20H6.5C5.1 20 4 18.9 4 17.5Z",
        "M4.5 16.5L9 12L13 16L15.5 13.5L19.5 17.5",
        "M16.5 8.5A1.5 1.5 0 1 1 13.5 8.5A1.5 1.5 0 1 1 16.5 8.5Z",
    )
    val Archivo = icono(
        "archivo",
        "M13.5 3H7.5C6.4 3 5.5 3.9 5.5 5V19C5.5 20.1 6.4 21 7.5 21H16.5C17.6 21 18.5 20.1 18.5 19V8Z",
        "M13.5 3V8H18.5",
        "M9 13H15",
        "M9 16.5H13",
    )
    val Lapiz = icono(
        "lapiz",
        "M4.5 19.5L5.2 15.8L15.4 5.6C16.2 4.8 17.4 4.8 18.2 5.6L18.4 5.8C19.2 6.6 19.2 7.8 18.4 8.6L8.2 18.8Z",
        "M14 7L17 10",
    )
    val Basura = icono(
        "basura",
        "M4.5 7H19.5",
        "M10 4H14",
        "M6.5 7L7.4 18.6C7.5 19.9 8.5 21 9.8 21H14.2C15.5 21 16.5 19.9 16.6 18.6L17.5 7",
        "M10 11V16.5",
        "M14 11V16.5",
    )
    val Compartir = icono(
        "compartir",
        "M18.5 5.5A2.5 2.5 0 1 1 13.5 5.5A2.5 2.5 0 1 1 18.5 5.5Z",
        "M8.5 12A2.5 2.5 0 1 1 3.5 12A2.5 2.5 0 1 1 8.5 12Z",
        "M18.5 18.5A2.5 2.5 0 1 1 13.5 18.5A2.5 2.5 0 1 1 18.5 18.5Z",
        "M8.2 10.8L13.8 6.7",
        "M8.2 13.2L13.8 17.3",
    )
    val Chat = icono(
        "chat",
        "M4.5 19.5L5.6 15.9C4.9 14.8 4.5 13.4 4.5 12C4.5 7.9 7.9 4.5 12 4.5C16.1 4.5 19.5 7.9 19.5 12C19.5 16.1 16.1 19.5 12 19.5C10.6 19.5 9.3 19.1 8.1 18.5Z",
    )
    val Reloj = icono(
        "reloj",
        "M20 12A8 8 0 1 1 4 12A8 8 0 1 1 20 12Z",
        "M12 7.5V12L15 14",
    )
    val Personas = icono(
        "personas",
        "M12.5 8A3.5 3.5 0 1 1 5.5 8A3.5 3.5 0 1 1 12.5 8Z",
        "M3 19.5C3 16.5 5.7 14 9 14C12.3 14 15 16.5 15 19.5",
        "M15.5 4.8C17 5.2 18 6.5 18 8C18 9.5 17 10.8 15.5 11.2",
        "M17.5 14.4C19.6 15 21 16.8 21 19.5",
    )
    val Recibo = icono(
        "recibo",
        "M6 3.5H18V20.5L16 19L14 20.5L12 19L10 20.5L8 19L6 20.5Z",
        "M9 8H15",
        "M9 11.5H15",
        "M9 15H12.5",
    )
    val Copiar = icono(
        "copiar",
        "M9.5 9H17.5C18.3 9 19 9.7 19 10.5V18.5C19 19.3 18.3 20 17.5 20H9.5C8.7 20 8 19.3 8 18.5V10.5C8 9.7 8.7 9 9.5 9Z",
        "M16 6V5.5C16 4.7 15.3 4 14.5 4H6.5C5.7 4 5 4.7 5 5.5V13.5C5 14.3 5.7 15 6.5 15H7",
    )
    val Chispa = icono(
        "chispa",
        "M11 4C11.5 8.3 12.7 9.5 17 10C12.7 10.5 11.5 11.7 11 16C10.5 11.7 9.3 10.5 5 10C9.3 9.5 10.5 8.3 11 4Z",
        "M17.5 15V20",
        "M15 17.5H20",
    )
    val Guardar = icono(
        "guardar",
        "M7 3.5H17C17.6 3.5 18 3.9 18 4.5V20.5L12 16.5L6 20.5V4.5C6 3.9 6.4 3.5 7 3.5Z",
    )
    val Exportar = icono(
        "exportar",
        "M12 15V4",
        "M8 8L12 4L16 8",
        "M5 14V17.5C5 18.9 6.1 20 7.5 20H16.5C17.9 20 19 18.9 19 17.5V14",
    )
    val Importar = icono(
        "importar",
        "M12 4V15",
        "M8 11L12 15L16 11",
        "M5 14V17.5C5 18.9 6.1 20 7.5 20H16.5C17.9 20 19 18.9 19 17.5V14",
    )
    val Banco = icono(
        "banco",
        "M3.5 9L12 4.5L20.5 9",
        "M6 11V16.5",
        "M10 11V16.5",
        "M14 11V16.5",
        "M18 11V16.5",
        "M3.5 19.5H20.5",
    )
    /** Partes iguales: el signo de dividir. */
    val Repartido = icono(
        "repartido",
        "M5 12H19",
        "M13 6.5A1 1 0 1 1 11 6.5A1 1 0 1 1 13 6.5Z",
        "M13 17.5A1 1 0 1 1 11 17.5A1 1 0 1 1 13 17.5Z",
    )
    /** Cada uno lo suyo: una lista. */
    val Detallado = icono(
        "detallado",
        "M9.5 6.5H19.5",
        "M9.5 12H19.5",
        "M9.5 17.5H19.5",
        "M5 6.5H5.01",
        "M5 12H5.01",
        "M5 17.5H5.01",
    )
    val Alerta = icono(
        "alerta",
        "M10.3 5L3.6 16.8C2.8 18.1 3.8 19.8 5.3 19.8H18.7C20.2 19.8 21.2 18.1 20.4 16.8L13.7 5C13 3.7 11 3.7 10.3 5Z",
        "M12 9.5V13.5",
        "M12 16.5H12.01",
    )
    val Ajustes = icono(
        "ajustes",
        "M4 7H11",
        "M15 7H20",
        "M4 17H7",
        "M11 17H20",
        "M15 7A2 2 0 1 1 11 7A2 2 0 1 1 15 7Z",
        "M11 17A2 2 0 1 1 7 17A2 2 0 1 1 11 17Z",
    )
    val Regalo = icono(
        "regalo",
        "M4.5 9.5H19.5V12.5H4.5Z",
        "M6 12.5V19C6 19.6 6.4 20 7 20H17C17.6 20 18 19.6 18 19V12.5",
        "M12 9.5V20",
        "M12 9.5C10.5 9.5 8 9 8 7C8 5.9 8.9 5 10 5.5C11.3 6.1 12 9.5 12 9.5Z",
        "M12 9.5C13.5 9.5 16 9 16 7C16 5.9 15.1 5 14 5.5C12.7 6.1 12 9.5 12 9.5Z",
    )
}

private fun icono(nombre: String, vararg trazados: String): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        trazados.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                // Negro: Icon lo tiñe con el color que se le pida
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
