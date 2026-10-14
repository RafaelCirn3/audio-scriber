plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.audioscriber"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.audioscriber"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2-beta"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
}
