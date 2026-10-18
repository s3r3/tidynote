plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}
application { mainClass.set("com.notarapi.backend.ProxyServerKt") }

kotlin { jvmToolchain(17) }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.ratelimit)
    implementation(libs.ktor.server.status)
    // Client CIO untuk memanggil provider LLM dari backend.
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.kotlinx.serialization.json)
}

// Cara pakai (setelah online):
//   PROVIDER_API_KEY=sk-xxxx APP_TOKENS=token-rahasia-untuk-app ./gradlew :backend:run
// Lalu di aplikasi (Settings): URL backend = http://IP-SERVER:8080,
// Token backend = salah satu APP_TOKENS di atas.
