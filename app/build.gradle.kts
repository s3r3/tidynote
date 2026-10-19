import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.notarapi.tidynote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notarapi.tidynote"
        // 29 (Android 10): MediaStore.Downloads + MediaColumns.RELATIVE_PATH
        // baru ada di API 29. Di bawah itu ekspor CSV/PDF melempar NoSuchFieldError
        // — itu Error, bukan Exception, jadi lolos dari catch(Exception) di
        // CsvExporter/PdfExporter dan bikin aplikasi crash. Nol referensi minSdk
        // di source, jadi tidak ada cabang kode yang perlu dibersihkan.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ------------------------------------------------------------------
        // MODE DEVELOPMENT: kredensial AI dibaca dari local.properties
        // (gitignored) → BuildConfig. Lihat peringatan di local.properties:
        // nilai ini IKUT di dalam APK, hanya untuk internal/demo.
        // PRODUKSI: biarkan LLM_API_KEY kosong → aplikasi memakai backend
        // proxy (URL + token diisi lewat Pengaturan), tidak ada secret di APK.
        // ------------------------------------------------------------------
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        fun lp(key: String, fallback: String = ""): String =
            localProps.getProperty(key, fallback)

        buildConfigField("String", "LLM_API_KEY", "\"${lp("LLM_API_KEY")}\"")
        buildConfigField("String", "LLM_BASE_URL", "\"${lp("LLM_BASE_URL", "https://api.openai.com/v1")}\"")
        buildConfigField("String", "LLM_MODEL", "\"${lp("LLM_MODEL", "gpt-4o-mini")}\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom.platform))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.test.manifest)

    // Koin DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.play.services)

    // HTTP Client
    implementation(libs.okhttp)

    

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ML Kit - bundled text recognition (includes latin)
    implementation(libs.mlkit.text.recognition)

    // ML Kit Document Scanner (Play Services)
    implementation(libs.mlkit.document.scanner)

    // Coil
    implementation(libs.coil.compose)

    // DataStore (pengganti SharedPreferences, plan §8)
    implementation(libs.datastore.preferences)

    // WorkManager (retry ekstraksi latar)
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.test.junit4)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    version.set("9.4.1")
    debug.set(false)
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
}