plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ibad.foldecho"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ibad.foldecho"
        minSdk = 31
        targetSdk = 34
        // The release workflow derives these from the pushed tag (so a tag and
        // its APK's version can never drift apart) and exports them as env vars
        // before building; unset for local/day-to-day builds, which fall back
        // to the hardcoded values below.
        versionCode = System.getenv("RELEASE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("RELEASE_VERSION_NAME") ?: "1.0.0"
    }

    // Release signing comes entirely from environment variables so no keystore
    // or password ever needs to live in this file or in git. Only the release
    // workflow (which decodes the RELEASE_KEYSTORE_BASE64 secret to a temp file
    // and exports these vars) sets RELEASE_KEYSTORE_PATH; a plain local or CI
    // `assembleRelease` without it just produces an unsigned APK instead of
    // failing, so this doesn't affect assembleDebug or day-to-day builds at all.
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
}
