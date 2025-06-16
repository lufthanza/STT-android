// settings.gradle.kts

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
        // TAMBAHKAN BARIS INI UNTUK FFMPEG-KIT
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "STT google ml kit"
include(":app")