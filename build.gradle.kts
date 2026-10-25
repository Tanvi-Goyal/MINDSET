plugins {
    alias(libs.plugins.spotless)
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotzilla) apply false
}

spotless {
    // Only files changed relative to main are checked, so existing code gets formatted as it is
    // touched instead of in one repo-wide reformat. CI checkout needs full history for this.
    ratchetFrom("origin/main")
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/.claude/**/*.kt")
        ktlint().editorConfigOverride(
            mapOf(
                "indent_size" to "4",
                "continuation_indent_size" to "4",
                "max_line_length" to "150",
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_no-empty-file" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**/*.gradle.kts", "**/.claude/**/*.gradle.kts")
        ktlint().editorConfigOverride(
            mapOf(
                "indent_size" to "4",
                "continuation_indent_size" to "4",
                "max_line_length" to "150",
            ),
        )
    }
    format("misc") {
        target("**/*.md", "**/.gitignore")
        targetExclude("**/.claude/**")
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
}
