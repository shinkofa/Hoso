import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load signing config from local.properties (populated by scripts/fetch-signing.sh).
// Absent in CI dev builds; release builds without it fall back to the debug key.
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.theermite.hoso"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.theermite.hoso"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProps.getProperty("signing.storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = signingProps.getProperty("signing.storePassword")
                keyAlias = signingProps.getProperty("signing.keyAlias")
                keyPassword = signingProps.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingProps.getProperty("signing.storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.recyclerview)

    implementation(libs.streampack.core)
    implementation(libs.streampack.services)
    implementation(libs.streampack.rtmp)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
