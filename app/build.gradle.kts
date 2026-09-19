import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ibad.foldecho"
    compileSdk = 35

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

    buildFeatures {
        compose = true
    }
    // No composeOptions.kotlinCompilerExtensionVersion here: on Kotlin 2.x the
    // org.jetbrains.kotlin.plugin.compose plugin (applied above) owns the
    // compose compiler version, matching Kotlin's own version automatically.
    // Setting that property alongside it is a build error, not a no-op.
}

// android.kotlinOptions { jvmTarget = "1.8" } — the block that used to live
// inside android {} above — is a hard compile error on Kotlin Gradle Plugin
// 2.3.x, not just a deprecation: "Using 'jvmTarget: String' is an error.
// Please migrate to the compilerOptions DSL." (confirmed by an actual CI
// failure on this exact line, not the migration guide alone). This top-level
// `kotlin {}` block, the DSL's replacement, is unrelated to the compileSdk/
// AGP/compose-bom bumps above or to the backdrop dependency itself — it would
// have needed fixing from the Kotlin 1.9.24 -> 2.x bump alone.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    // Originally pinned to 0.7.3 (not the latest release) because it was the
    // last version published against this project's then-toolchain
    // (kotlin-stdlib 1.9.24, compose-ui 1.6.7) — newer Haze releases moved to
    // Compose Multiplatform coordinates built with Kotlin 2.x. The Kotlin 2.x /
    // compose-bom 2025.12.01 migration below (for the `backdrop` dependency
    // just under this one) removes the reason for that pin: 0.7.3 is still
    // declared here, untouched, so a build failure lands squarely on whichever
    // change actually caused it rather than on a pin bumped at the same time
    // for a different reason. If CI turns up a Haze/Compose-UI incompatibility
    // now that both are on Kotlin 2.x's classpath, bumping Haze off 0.7.3 is
    // the next thing to try — not done here pre-emptively.
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // KMP Liquid Glass (github.com/Kashif-E/KMPLiquidGlass), published to Maven
    // Central as `backdrop`. Only version on Central as of this writing.
    //
    // This is *why* the toolchain above was bumped: 0.0.1-alpha02's published
    // Gradle module metadata declares kotlin-stdlib >= 2.3.0 and
    // androidx.compose.ui/foundation >= 1.10.0 as hard requirements, which the
    // project's previous toolchain (Kotlin 1.9.24, compose-bom 2024.06.00,
    // compileSdk 34, AGP 8.5.0) could not satisfy — confirmed by an actual
    // Gradle failure (AAR metadata check: compileSdk >= 35 / AGP >= 8.6.0
    // required), not inferred from the module metadata alone.
    implementation("io.github.kashif-mehmood-km:backdrop:0.0.1-alpha02")
}
