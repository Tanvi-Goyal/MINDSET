import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mindset.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "com.mindset.feature.paywall"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime) // the Compose compiler runs on every target
        }
        androidMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // RevenueCat's hosted Paywall. Declared in androidMain rather than commonMain
            // deliberately: the composable is Android-only for now (port-later decision), and
            // keeping it out of commonMain spares the iOS build the -ui klib, its cinterop opt-in,
            // and its iOS 15.0 deployment floor until iOS parity actually lands.
            implementation(libs.purchases.ui)
        }
    }
}
