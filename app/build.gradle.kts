// Pastikan blok 'plugins' ada di paling atas
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Blok 'android' sekarang akan dikenali karena plugin di atas
android {
    namespace = "com.example.videosubtitlegenerator" // Ganti dengan package name Anda
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videosubtitlegenerator" // Ganti dengan package name Anda
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    // Blok buildFeatures untuk mengaktifkan ViewBinding
    buildFeatures {
        viewBinding = true
    }
}

// Blok 'dependencies' sekarang akan dikenali
dependencies {

    // UI standar
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)

    // Coroutines untuk menangani tugas di background
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // Untuk await() pada Google Play Services Task

    // Google ML Kit Speech Recognition
    // DIGANTI: Gunakan versi stabil yang lebih baru
    implementation("com.google.android.gms:play-services-mlkit-speech-transcription:19.0.0")

    // FFmpeg-Kit untuk ekstraksi audio
    // DIGANTI: Hapus ".LTS" dari nomor versi
    implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0")

    // Dependensi standar lainnya
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}