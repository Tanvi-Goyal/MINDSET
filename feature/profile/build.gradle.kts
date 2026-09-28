import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :feature:profile — the Profile/Preferences feature. KMP: the ViewModel + Koin module live in
 * commonMain (shared with iOS); the Compose screens + their NavGraphBuilder extensions live in
 * androidMain. Depends only on :core:domain (commonMain) and the UI/design/nav cores (androidMain) —
 * never on :core:data / :core:sync / other features.
 */
plugins {
    id("mindset.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "com.mindset.feature.profile"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain) // PreferencesRepository + ThemeMode/WeightUnit/UserPreferences
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            // Compose runtime is multiplatform and must be on EVERY target's classpath: the Compose
            // compiler plugin runs on the iOS compilations too and fails if it can't find the runtime,
            // even though only androidMain has @Composable screens.
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(projects.feature.paywall)
            implementation(projects.core.ui) // MindSetBottomBar, Tab
            implementation(projects.core.designsystem) // MindSetTheme + color tokens
            implementation(projects.core.navigation) // Credits typed route
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.runtimeCompose) // collectAsStateWithLifecycle
            implementation(libs.androidx.navigation.compose) // NavGraphBuilder / composable
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose.viewmodel) // koinViewModel()
        }
    }
}
