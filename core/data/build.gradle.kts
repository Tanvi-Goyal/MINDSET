import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mindset.kmp.library")
}

kotlin {
    androidLibrary {
        namespace = "com.mindset.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain) // implements the repository interfaces
            implementation(projects.core.database) // AppDatabase, DAOs, entities, importer, asset reader
            implementation(projects.core.datastore) // DataStore<Preferences> (device-local settings)
            implementation(projects.core.network) // WgerApi (MuscleImageProvider), RaceCalendarApi
            implementation(projects.contracts) // HyroxCalendarSeed: the bundled race calendar
            implementation(projects.core.model) // mappers produce domain models
            implementation(projects.core.common) // UuidGenerator
            implementation(libs.room.runtime) // useWriterConnection / immediateTransaction
            implementation(libs.androidx.datastore.preferences.core) // PreferencesRepositoryImpl
            implementation(libs.androidx.paging.common) // Pager / PagingData.map
            implementation(libs.kotlinx.coroutines.core)
            // dataModule (DI): repo impls bound to domain interfaces + MuscleImageProvider.
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(projects.contracts) // HyroxCalendarSeed in RaceCalendarRepositoryTest
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.sqlite.bundled) // BundledSQLiteDriver for in-memory test DBs
        }
    }
}
