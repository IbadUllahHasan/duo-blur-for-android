// Known-good baseline before the migration below: AGP 8.5.0, Kotlin 1.9.24,
// compileSdk 34 (app/build.gradle.kts), compose-bom 2024.06.00 (same file),
// kotlinCompilerExtensionVersion 1.5.14 (same file, via composeOptions — the
// mechanism Kotlin 2.x replaces with the compose compiler plugin below).
// Last commit on that baseline: d005a7e ("Add an in-app Light/Dark/System
// appearance control"), CI green at
// https://github.com/IbadUllahHasan/duo-blur-for-android/actions/runs/35437174821
// `git checkout d005a7e -- build.gradle.kts app/build.gradle.kts` restores it
// exactly if this migration doesn't land cleanly.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    // Kotlin 2.x moved the Compose compiler out of AGP's (now-removed)
    // kotlinCompilerExtensionVersion and into its own Gradle plugin, versioned
    // in lockstep with Kotlin itself rather than separately.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
