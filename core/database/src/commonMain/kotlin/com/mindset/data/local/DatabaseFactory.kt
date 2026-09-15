package com.mindset.data.local

import androidx.room3.RoomDatabase

// The SQLite driver is set by each platform's builder (androidDatabaseBuilder / iosDatabaseBuilder),
// not here: Android needs the framework driver in debug so the Database Inspector can attach, while
// native must always use the bundled driver. Migrations stay centralized in this shared factory.
fun buildDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    // Real versioned migration (no more destructive fallback): existing installs upgrade v7 → v8
    // without data loss. See [MIGRATION_7_8]. Future schema changes add the next migration here.
    .addMigrations(
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
    ).build()
