import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mindset.kmp.library")
}

kotlin {
    androidLibrary {
        namespace = "com.mindset.billing"
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
        withHostTest {}
    }

    // RevenueCat's iOS half is a cinterop over PurchasesHybridCommon, so every iOS source set has
    // to opt in explicitly — without this the iOS compilation fails on the SDK's own signatures.
    sourceSets.named { it.lowercase().startsWith("ios") }.configureEach {
        languageSettings { optIn("kotlinx.cinterop.ExperimentalForeignApi") }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain) // EntitlementRepository — the seam we feed
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.purchases.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
