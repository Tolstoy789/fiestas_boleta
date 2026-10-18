# Divisor de Cuentas (Android)

App nativa en Kotlin para dividir una boleta entre participantes. Todo corre en el
teléfono, sin internet: la foto se lee con ML Kit y Gemma ordena los ítems con
Google AI Edge. Pensada para el Samsung Galaxy S25 Ultra (sirve en Android 12 o superior).

## Cómo funciona

1. **Boleta:** tomas una foto o eliges una de la galería.
2. **OCR:** ML Kit extrae el texto y lo ordena por filas, para que cada ítem quede junto a su precio.
3. **Gemma:** el texto se envía al modelo con un prompt que pide un JSON `[{item, cantidad, precio}]`.
   Si el modelo responde algo ilegible, la app lee los ítems con expresiones regulares.
4. **Ítems:** revisas y corriges lo leído.
5. **Asignación:** creas participantes y marcas quién consumió cada ítem.
6. **Resumen:** cada persona paga sus ítems más su parte proporcional de propina e impuesto.
   El resultado se puede compartir por WhatsApp.

## Estructura

| Archivo | Qué hace |
|---|---|
| `core/.../DivisorCuenta.kt` | Cálculo del reparto (Kotlin puro, con pruebas). |
| `core/.../FilasOcr.kt` | Reordena las líneas del OCR en filas ítem + precio. |
| `core/.../ExtraccionItems.kt` | Prompt para Gemma, lector del JSON y respaldo por expresiones regulares. |
| `app/.../ocr/OcrBoleta.kt` | OCR con ML Kit. |
| `app/.../llm/MotorLlm.kt` | Interfaz `MotorLlm` con dos motores: `MotorMock` (sin modelo) y `MotorAiEdge` (Gemma). |
| `app/.../llm/ModeloLocal.kt` | Copia el archivo del modelo dentro de la app. |
| `app/.../ui/` | Estado (`CuentaViewModel`) y las cuatro pantallas en Jetpack Compose. |

Para conectar otro motor (otro SDK de AI Edge, LiteRT-LM, un servidor local), implementa
`MotorLlm.generar(prompt)` y devuélvelo en `CuentaViewModel.motor()`.

## Reglas del reparto

- Un ítem marcado para varias personas se divide en partes iguales entre ellas.
- Un ítem sin marcar se reparte entre todos.
- Propina e impuesto se reparten en proporción a lo que consumió cada uno.
- Por defecto el impuesto se considera incluido en los precios (como el IVA en Chile); se puede desactivar.
- Los montos se redondean a pesos o centavos y la suma siempre cuadra con el total.

## Compilar e instalar en el teléfono

1. Abre la carpeta `android/` en Android Studio (Ladybug o más reciente) y deja que sincronice Gradle.
2. En el S25: Ajustes > Acerca del teléfono > Información de software, toca 7 veces "Número de compilación"
   para activar las opciones de desarrollador, y luego activa "Depuración USB".
3. Conecta el teléfono por USB, elígelo en la barra superior de Android Studio y pulsa Run.

Sin modelo la app ya funciona en modo de prueba (lee los ítems sin IA).

## Conectar Gemma

AI Edge Gallery guarda sus modelos en una carpeta privada que otras apps no pueden leer,
así que hay que descargar el archivo `.task` aparte:

1. En el teléfono, descarga un modelo Gemma en formato `.task` desde Hugging Face
   (por ejemplo `gemma-3n-E2B-it-int4.task`, de la página de Google `gemma-3n-E2B-it-litert-preview`;
   hay que aceptar la licencia de Gemma con tu cuenta).
2. En la app, pantalla 1, toca **Importar modelo .task** y elige el archivo. Se copia una sola vez.
3. Activa **Usar Gemma (AI Edge)**. La primera carga del modelo tarda unos segundos; se intenta usar la GPU
   y, si no se puede, la CPU.

## Pruebas

```bash
./gradlew :core:test
```

13 pruebas: reparto, agrupación de filas del OCR, lectura de precios (`12.000`, `12,50`, `1.234,56`) y del JSON.
