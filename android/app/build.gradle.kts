plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.adgamepriority.ad_fast_dismiss"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.adgamepriority.ad_fast_dismiss"
        // API 24+ required for dispatchGesture in AccessibilityService
        minSdk = 24
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            storeFile = file("upload-keystore.jks")
            storePassword = "playblitz2026"
            keyAlias = "playblitz"
            keyPassword = "playblitz2026"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Enable R8 shrinking but keep our rules
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Required for NotificationCompat, NotificationChannel etc.
    implementation("androidx.core:core-ktx:1.13.1")
}
