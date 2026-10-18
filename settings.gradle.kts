import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TidyNote"

include(":app")
// Backend Ktor (deploy terpisah). Isi env PROVIDER_API_KEY + APP_TOKENS,
// lalu `./gradlew :backend:run`. Lihat INTEGRATION_NOTES.md.
include(":backend")
