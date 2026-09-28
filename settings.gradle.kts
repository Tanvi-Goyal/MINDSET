rootProject.name = "MindSet"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app")
include(":shared")
include(":contracts")
include(":server")
include(":benchmark")
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:billing")
include(":core:domain")
include(":core:data")
include(":core:sync")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":feature:profile")
include(":feature:stations")
include(":feature:history")
include(":feature:exercises")
include(":feature:logging")
include(":feature:templates")
include(":feature:home")
include(":feature:auth")
include(":feature:onboarding")
include(":feature:paywall")
