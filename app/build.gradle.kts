plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    buildFeatures {
        buildConfig = true
    }

    namespace = "com.orkeit.desktop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orkeit.desktop"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("1.0.0").get()
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "true")
        }
    }

    signingConfigs {
        create("playRelease") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("playRelease")
            isMinifyEnabled = false
        }
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
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
