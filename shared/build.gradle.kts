import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotzilla) apply false
}

// Kotzilla Koin profiler: OFF unless you ask for it with `-Pmindset.profiler=true`.
//
// It is a compile-time switch, not a runtime one. The Kotzilla SDK ships consumer ProGuard rules
// that -keep its own classes, which makes them R8 entry points — so once the artifact is on the
// classpath it CANNOT be shrunk out of a release build, however unreachable the calling code is.
// Verified the hard way: a BuildConfig.DEBUG gate still left 117 SDK references (KotzillaSDK,
// KotzillaService, the gateway URL) in a minified release APK. Keeping it off the classpath is the
// only thing that actually works.
val profilerEnabled: Boolean = providers.gradleProperty("mindset.profiler").orNull == "true"

if (profilerEnabled) {
    apply(plugin = libs.plugins.kotzilla.get().pluginId)
}

// Same source of truth as :app's versionName (gradle/libs.versions.toml), so a profiler session
// can be attributed to a specific release.
val appVersionName: String = listOf(
    libs.versions.app.versionMajor,
    libs.versions.app.versionMinor,
    libs.versions.app.versionPatch,
).joinToString(".") { it.get() }

if (profilerEnabled) {
    configure<io.kotzilla.gradle.ext.KotzillaExtension> {
        versionName.set(appVersionName)
    }
}

kotlin {
    // Room-KMP generates `actual` objects for @ConstructedBy; the expect/actual-classes feature
    // is still Beta in Kotlin, so opt in explicitly to silence the per-target warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "com.mindset.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Export the modules whose types cross the Swift boundary so they enter Shared.framework's
            // Obj-C header (KoinIos returns the VMs; Swift holds them + casts their UI-state types).
            export(projects.core.model) // Exercise, MuscleDiagram
            export(projects.core.common) // FlowSubscription
            export(projects.core.domain) // UserPreferences
            export(projects.feature.home)
            export(projects.feature.logging)
            export(projects.feature.templates)
            export(projects.feature.exercises)
            export(projects.feature.history)
            export(projects.feature.stations)
            export(projects.feature.profile)
        }
    }

    sourceSets {
        // Exactly one of these is on the source path, and it decides whether `appObservability`
        // wires up the profiler or does nothing.
        commonMain.get().kotlin.srcDir(
            if (profilerEnabled) "src/profilerMain/kotlin" else "src/noProfilerMain/kotlin",
        )
        if (profilerEnabled) {
            commonMain.dependencies { implementation(libs.kotzilla.sdk) }
        }
        commonMain.dependencies {
            // `api` so consumers (composeApp today, the iOS umbrella later) see these leaf modules
            // transitively — the moved packages (`com.mindset.model`/`common`) keep their names, so
            // no import in :shared or :composeApp changes.
            api(projects.core.model)
            api(projects.core.common)
            // `api`: repo interfaces + domain value types (UserPreferences/WeightUnit/ThemeMode) are
            // referenced by the ViewModels here and by composeApp's theme/profile UI.
            api(projects.core.domain)
            // The repository impls + MuscleImageProvider (bound in DI here); package unchanged.
            implementation(projects.core.data)
            // dataStorePlatformModule (device-local settings seam), aggregated in Modules.kt below.
            implementation(projects.core.datastore)
            // `api` (not implementation) is transitional: composeApp still references some entity
            // types (ExerciseMetric/Session/Exercise/VolumePoint/PlannedSession) — a UI→database leak
            // cleaned up when features are extracted (B11) and those types move to :core:model.
            api(projects.core.database)
            implementation(projects.core.network)
            // billingModule (EntitlementSyncer), aggregated in Modules.kt. `implementation`, not
            // `api`: nothing from billing is re-exported to Swift, and :app declares :core:billing
            // itself for the two startup calls. Screens read entitlement state through
            // EntitlementRepository like any other repository.
            implementation(projects.core.billing)
            implementation(projects.core.sync) // SyncEngine, consumed by HomeViewModel + bound in Koin
            implementation(projects.contracts)
            // Feature modules — `api` so their ViewModels stay exported in Shared.framework for Swift (B11).
            api(projects.feature.profile)
            api(projects.feature.stations)
            api(projects.feature.history)
            api(projects.feature.exercises)
            api(projects.feature.logging)
            api(projects.feature.templates)
            api(projects.feature.home)
            // Onboarding VM graph. `api` for Koin aggregation in Modules.kt; not exported to the iOS
            // framework (Android-first per the port-later decision) — add export(...) when iOS parity lands.
            api(projects.feature.onboarding)
            // Room RUNTIME stays: the repository + sync engine + iosTest still call useWriterConnection /
            // Room.inMemoryDatabaseBuilder. Only the Room *plugin*/KSP/schemas moved to :core:database.
            implementation(libs.room.runtime)
            implementation(libs.room.paging)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        androidMain.dependencies {
            // Koin + Ktor engine bindings moved to :core:database / :core:network platform seams (B7).
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
