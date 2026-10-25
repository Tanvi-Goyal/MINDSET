import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

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

    // RevenueCat's klib hardcodes its build machine's Xcode path for the Swift back-deploy libs
    // (swiftCompatibility56, …), so iOS test executables fail to link on any other Xcode install.
    // Point the linker at the active Xcode instead. Mac-only: Linux CI never links iOS binaries.
    if (providers.systemProperty("os.name").get().startsWith("Mac")) {
        val developerDir = providers.exec { commandLine("xcode-select", "-p") }
            .standardOutput.asText.get().trim()
        targets.withType<KotlinNativeTarget>().configureEach {
            val platform = if (name.endsWith("SimulatorArm64")) "iphonesimulator" else "iphoneos"
            binaries.withType<TestExecutable>().configureEach {
                linkerOpts("-L$developerDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$platform")
            }
        }
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
