import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Release signing is optional and opt-in via a local, gitignored `keystore.properties` (see
// README "Generar tu propio APK") — without it, `assembleRelease` still works exactly as before
// (unsigned output), so this doesn't change behavior for anyone who hasn't set one up.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Credenciales de DESARROLLADOR de Ginny ante ScreenScraper, también opcionales y fuera de git
// (`scrapper.properties`, plantilla en `scrapper.properties.example`). Se incrustan en el APK como
// campos de BuildConfig: identifican al programa, no al usuario. Sin el archivo quedan vacías y el
// scrapper de ScreenScraper se desactiva, pero el proyecto compila igual.
val scrapperPropertiesFile = rootProject.file("scrapper.properties")
val scrapperProperties = Properties().apply {
    if (scrapperPropertiesFile.exists()) {
        scrapperPropertiesFile.inputStream().use { load(it) }
    }
}

/** Valor como literal de String para `buildConfigField` (con comillas y barras escapadas). */
fun String.asBuildConfigString(): String = "\"" + trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.ginny.frontend"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ginny.frontend"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SCREENSCRAPER_DEV_ID", scrapperProperties.getProperty("screenscraper.devId", "").asBuildConfigString())
        buildConfigField("String", "SCREENSCRAPER_DEV_PASSWORD", scrapperProperties.getProperty("screenscraper.devPassword", "").asBuildConfigString())
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // campos de scrapper.properties (ver arriba)
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Names the release APK "GinnyFrontend-<versionName>.apk" instead of the default
// "app-release.apk" — reads versionName from defaultConfig, so bumping it for a new release (see
// README "Generar tu propio APK") automatically renames the next build's output too.
// AGP 8's new Variant API (androidComponents/onVariants) dropped a public, non-internal way to set
// this (VariantOutput only exposes versionCode/versionName, verified against the real AGP 8.4.2
// jars with javap) — the old applicationVariants API is still functional for this in 8.4.2 and is
// what's used here, cast to the internal impl class to reach the outputFileName setter.
android.applicationVariants.all {
    if (buildType.name == "release") {
        val apkFileName = "GinnyFrontend-$versionName.apk"
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkFileName
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-process:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.6.0")

    // Cliente HTTP del scrapper. Misma versión que Coil 2.6 ya trae de forma transitiva (se declara
    // para no depender de ese detalle).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Desenfoque de lo que hay detrás de un elemento (backdrop blur), que Compose 1.6 no trae.
    // Serie 0.7.x = la compatible con Compose 1.6 / Kotlin 1.9. Blur real en API 31+; en 29-30
    // cae a un tinte translúcido sin desenfoque.
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Pruebas unitarias en JVM (lógica pura: NameNormalizer, ScrapRedactor, parsers del scrapper).
    testImplementation("junit:junit:4.13.2")
    // En JVM, org.json de android.jar es un esqueleto que lanza "not mocked": se usa la implementación real.
    testImplementation("org.json:json:20240303")
}
