plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "uz.yoldosh.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.yoldosh.app"
        minSdk = 26
        targetSdk = 33
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("shared") {
            storeFile = file("yoldosh-dev.keystore")
            storePassword = "yoldosh"
            keyAlias = "yoldosh"
            keyPassword = "yoldosh"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("glb", "onnx")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    // Offline speech synthesis; the AAR is downloaded by the CI build (see .github/workflows/build.yml)
    implementation(files("libs/sherpa-onnx.aar"))
}
