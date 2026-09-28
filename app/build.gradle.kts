import com.android.build.api.variant.BuildConfigField
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}

val hasFirebaseConfig: Boolean = project.file("google-services.json").exists()

// App version, from gradle/libs.versions.toml. versionCode is DERIVED rather than maintained
// alongside versionName, because Play permanently rejects a code <= one already published and a
// hand-kept pair drifts silently. The packing keeps codes ordered exactly as semver orders
// releases; ceiling 99.99.99 -> 999_999, well under Play's 2_100_000_000 limit.
val versionMajor = libs.versions.app.versionMajor.get().toInt()
val versionMinor = libs.versions.app.versionMinor.get().toInt()
val versionPatch = libs.versions.app.versionPatch.get().toInt()
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"
val appVersionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch

// RevenueCat SDK keys, read from the gitignored local.properties. These are *publishable* client
// keys (not secrets like the signing keystore), but they stay out of git so a fork can't bill
// against this project's account and so debug/release can't be mixed up by accident.
// Absent on CI and on a fresh clone — debug degrades to an empty key (the paywall simply reports
// billing unavailable) rather than failing configuration.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val revenueCatDebugKey: String = localProperties.getProperty("revenuecat.apiKey.debug").orEmpty()
val revenueCatReleaseKey: String = localProperties.getProperty("revenuecat.apiKey.release").orEmpty()

if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
    apply(plugin = libs.plugins.firebaseCrashlytics.get().pluginId)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))

    if (project.findProperty("composeReports") == "true") {
        val dir = layout.buildDirectory.dir("compose_compiler")
        reportsDestination = dir
        metricsDestination = dir
    }
}
dependencies {
    implementation(projects.shared)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    // configureBilling + EntitlementSyncer, both started in MindSetApplication. Declared here
    // rather than leaned on transitively: :app is the only module that may hold the API key.
    implementation(projects.core.billing)
    implementation(projects.feature.profile)
    implementation(projects.feature.stations)
    implementation(projects.feature.history)
    implementation(projects.feature.exercises)
    implementation(projects.feature.logging)
    implementation(projects.feature.templates)
    implementation(projects.feature.home)
    implementation(projects.feature.auth)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)

    // Lazy-loaded exercise images (Coil 3, with OkHttp network fetcher + disk cache).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg) // wger muscle diagrams are SVG

    // Installs the baseline profile packaged in the APK at first run (Phase 3).
    implementation(libs.androidx.profileinstaller)

    if (hasFirebaseConfig) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
    }

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // The :benchmark module produces the baseline profile this app consumes.
    baselineProfile(projects.benchmark)

    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.compose.uiTestJunit4)
}

android {
    namespace = "com.mindset"
    // Needed only so AGP can find llvm-objcopy: we ship no native code of our own, but Room's
    // bundled SQLite driver and DataStore do, and without an NDK AGP silently packages their .so
    // files unstripped (~213 KB of symbol tables per ABI riding in the download).
    ndkVersion = libs.versions.android.ndk.get()
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // Deliberately NOT the same as `namespace` above. Play requires a globally unique
        // applicationId and `com.mindset` was already taken; the namespace only decides where R and
        // BuildConfig are generated, so leaving it as `com.mindset` keeps every Kotlin package and
        // import untouched. Play Console calls this field "Package name" — same thing.
        // Permanent once the Play app entry exists: Play does not allow reusing a package name.
        applicationId = "com.mindset.athlete"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Off everywhere by default; only the `benchmark` variant turns it on. MainActivity is
        // exported, so the bulk-seed intent extra it gates must never be honored in a shipped build.
        buildConfigField("boolean", "SEED_HOOK_ENABLED", "false")

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatDebugKey\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Upload-key config, read from a gitignored keystore.properties at the repo root. Absent on CI
    // and on a fresh clone, so this must degrade to "no release signing" rather than failing
    // configuration — an unsigned assembleRelease is a clearer failure than a broken build script.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    // Checks the FILE, not just the property: a stale path in keystore.properties otherwise fails
    // deep in the signing task ("Keystore file ... not found") on every release-derived variant,
    // including the nonMinifiedRelease one the baseline-profile plugin generates.
    val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")
        ?.let { rootProject.file(it).exists() } == true

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8: shrink + optimize + obfuscate. See proguard-rules.pro for what has to be kept —
            // the one real hazard here is enums persisted by `name`, not DI or serialization.
            isMinifyEnabled = true
            // Strips resources no kept code references. Legal only with minify on, because it
            // relies on R8's reachability graph. Anything looked up by name via
            // Resources.getIdentifier() is invisible to that graph — we have none today; if that
            // changes, list the survivors in res/raw/keep.xml rather than turning this off.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Extracts native symbols into the AAB (BUNDLE-METADATA/...debugsymbols/) so Play can
            // symbolicate native crashes, and lets AGP strip the shipped .so. SYMBOL_TABLE gives
            // function names — enough to read a stack trace; FULL adds line numbers and a much
            // larger upload, which is not worth it for libraries we do not maintain.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")

            // Play-billing key. Guarded by verifyReleaseRevenueCatKey below — a Test Store key
            // here would ship a paywall that can never actually charge anyone.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatReleaseKey\"")
        }
        // Non-debuggable, profileable variant Macrobenchmark runs against (it refuses debuggable
        // builds). Signed with the debug key so the release-like APK still installs locally.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            // initWith copied both R8 flags off `release`. Resource shrinking is only legal with
            // code shrinking on, so clearing minify alone leaves an invalid pair and AGP fails the
            // build. Macrobenchmark wants unobfuscated symbols anyway, so both stay off here.
            isShrinkResources = false
            matchingFallbacks += listOf("release")

            // Macrobenchmark needs a long History list to scroll; this is the only variant allowed
            // to honor :benchmark's EXTRA_SEED intent extra.
            buildConfigField("boolean", "SEED_HOOK_ENABLED", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The androidx.baselineprofile plugin synthesizes its own build types off `release`, so the
// SEED_HOOK_ENABLED flag set on our hand-written `benchmark` type never reaches the variant that
// actually runs BaselineProfileGenerator — the seed intent extra was silently ignored there.
//
// Enabled by an EXACT-NAME allowlist, never a build-type or prefix match. MainActivity is
// exported and this hook writes to the user's real database, so the set of variants that honor it
// has to be auditable by reading this list: `release` is deliberately absent, and a new variant
// cannot acquire the hook by accident.
val seedHookVariants = setOf("nonMinifiedRelease", "benchmarkRelease")

androidComponents {
    onVariants { variant ->
        if (variant.name in seedHookVariants) {
            variant.buildConfigFields?.put(
                "SEED_HOOK_ENABLED",
                BuildConfigField("boolean", true, "Benchmark-only: honors the bulk-seed extra."),
            )
        }
    }
}

// A Test Store key (`test_…`) never reaches Google Play billing: it simulates purchases in-app, so
// shipping one produces a paywall that renders, accepts a tap, and can never charge anyone —
// silently, with no crash to notice. RevenueCat's own docs are blunt about it ("Never submit an app
// ... configured with a Test Store API key"), so this is a hard build failure rather than a warning.
//
// Wired as a task dependency, not a configuration-time `check`, so it fails ONLY when a release
// artifact is actually being produced — `assembleDebug` on a fresh clone with no local.properties
// must still work.
val verifyReleaseRevenueCatKey = tasks.register("verifyReleaseRevenueCatKey") {
    group = "verification"
    description = "Fails the build if the release RevenueCat key is missing or is a Test Store key."
    doLast {
        check(revenueCatReleaseKey.isNotBlank()) {
            "Missing `revenuecat.apiKey.release` in local.properties. Add the Play (goog_…) SDK key " +
                "from RevenueCat → Project Settings → API keys."
        }
        check(!revenueCatReleaseKey.startsWith("test_")) {
            "`revenuecat.apiKey.release` is a Test Store key. Test Store purchases are simulated and " +
                "never reach Play billing — a release built with this key cannot take payment."
        }
    }
}

// Covers the bundle too: `bundleRelease` is what actually goes to Play, and it does not run
// assembleRelease.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseRevenueCatKey)
}
