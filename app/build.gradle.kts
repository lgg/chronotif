plugins {
    id("com.android.application")
}

// Release signing comes from the environment (CI secrets or a local shell).
// Without it, release builds fall back to the debug key so they stay installable.
val releaseKeystore: String? = System.getenv("CHRONOTIF_KEYSTORE")

android {
    namespace = "app.chronotif"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.chronotif"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CHRONOTIF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHRONOTIF_KEY_ALIAS")
                keyPassword = System.getenv("CHRONOTIF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    // No Google-encrypted dependency metadata blob in the APK.
    lint {
        // Target 36 on purpose until 37's behaviour changes are tested on real devices.
        disable += setOf("OldTargetApi", "GradleDependency")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
