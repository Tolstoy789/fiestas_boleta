plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.divisorcuentas"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.divisorcuentas"
        minSdk = 31 // Android 12+: colores dinámicos; el S25 trae Android 15
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // LiteRT-LM viene compilada con Kotlin 2.4; igual que AI Edge Gallery, se lee sin exigir la misma versión
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // OCR en el teléfono (modelo latino incluido en el APK)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Gemma en el teléfono con Google AI Edge: LiteRT-LM lee los .litertlm (Gemma 4)
    // y MediaPipe LLM Inference los .task (Gemma 3n)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    // MPImage/BitmapImageBuilder (para mandar la foto a Gemma) vienen en tasks-core, que trae tasks-vision
    implementation("com.google.mediapipe:tasks-vision:0.10.+") // 0.10.24 no existe para vision; toma la 0.10 más reciente
}
