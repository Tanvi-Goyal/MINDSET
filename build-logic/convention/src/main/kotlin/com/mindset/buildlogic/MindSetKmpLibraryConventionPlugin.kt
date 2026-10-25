package com.mindset.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Baseline for every KMP library module: applies the Kotlin Multiplatform + AGP KMP-library plugins,
 * declares the iOS targets, and opts into the expect/actual-classes feature (Room-KMP generates
 * `actual` objects). Modules add only their own `androidLibrary { namespace; compileSdk; minSdk }`,
 * any extra targets (e.g. `jvm()`), and dependencies.
 *
 * The android compileSdk/minSdk stay in each module's `androidLibrary {}` block: AGP 9's KMP-library
 * DSL isn't cleanly configurable from a precompiled convention plugin, and `namespace` is per-module
 * regardless — so centralizing only the pieces that are safe to reach keeps this plugin green.
 */
class MindSetKmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }

        // AGP's hostTest lint tasks read KSP's generated hostTest sources without depending on
        // kspAndroidHostTest, which Gradle rejects when both run in one build (as on CI).
        pluginManager.withPlugin("com.google.devtools.ksp") {
            val lintTasks = setOf("generateAndroidHostTestLintModel", "lintAnalyzeAndroidHostTest")
            tasks.matching { it.name in lintTasks }.configureEach {
                dependsOn(tasks.matching { it.name == "kspAndroidHostTest" })
            }
        }
    }
}
