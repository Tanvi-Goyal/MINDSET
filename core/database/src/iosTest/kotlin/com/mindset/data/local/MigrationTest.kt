package com.mindset.data.local

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the v7 → v8 fold survives on real legacy data. [MigrationTestHelper] builds the v7 DB from
 * `schemas/…/7.json`, we insert a legacy session tree, then `runMigrationsAndValidate` applies
 * [MIGRATION_7_8] and validates the result against `8.json` column-for-column — so a schema drift in
 * the migration SQL fails here, not in production. Runs on the iOS sim (the Native actual of
 * MigrationTestHelper is driver-based; the Android one is instrumentation-only).
 */
class MigrationTest {

    // The schema dir path is injected by the Gradle test task (see shared/build.gradle.kts) — Room
    // reads the exported JSON from disk at runtime, and the Native test's CWD isn't the module root.
    @OptIn(ExperimentalForeignApi::class)
    private val schemaDir: String = getenv("MINDSET_SCHEMA_DIR")?.toKString()
        ?: error("MINDSET_SCHEMA_DIR not set — check the KotlinNativeSimulatorTest env wiring")

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = schemaDir,
        fileName = NSTemporaryDirectory() + "mindset-migration-test.db",
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    private fun SQLiteConnection.long(sql: String): Long = prepare(sql).use { st ->
        st.step()
        st.getLong(0)
    }

    private fun SQLiteConnection.nullableLong(sql: String): Long? = prepare(sql).use { st ->
        st.step()
        if (st.isNull(0)) null else st.getLong(0)
    }

    private fun SQLiteConnection.text(sql: String): String? = prepare(sql).use { st ->
        st.step()
        if (st.isNull(0)) null else st.getText(0)
    }

    @Test
    fun folds_legacy_session_tree_into_blocks_and_backfills_tombstones() = runTest {
        helper.createDatabase(version = 7).apply {
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, notes, isTemplate, source, " +
                    "templateId, updatedAt, deleted, syncStatus) VALUES " +
                    "('s1', 1000, 'Upper', 'STRENGTH', NULL, 0, 'MANUAL', NULL, 2000, 0, 'SYNCED')",
            )
            // A soft-deleted, empty session — its tombstone must carry over, and it gets no block.
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, notes, isTemplate, source, " +
                    "templateId, updatedAt, deleted, syncStatus) VALUES " +
                    "('s2', 500, 'Old', 'STRENGTH', NULL, 0, 'MANUAL', NULL, 900, 1, 'SYNCED')",
            )
            execSQL(
                "INSERT INTO exercises (id, name, category, metric, primaryMuscles, secondaryMuscles, " +
                    "instructions, imageUrls, keywords) VALUES " +
                    "('bench-press', 'Bench', 'strength', 'WEIGHT_REPS', '', '', '', '', '')",
            )
            execSQL(
                "INSERT INTO logged_items (id, sessionId, exerciseId, orderIndex) VALUES ('li1', 's1', 'bench-press', 0)",
            )
            execSQL(
                "INSERT INTO set_entries (id, loggedItemId, setNumber, reps, loadKg) VALUES ('se1', 'li1', 1, 10, 60.0)",
            )
            execSQL(
                "INSERT INTO set_entries (id, loggedItemId, setNumber, reps, loadKg) VALUES ('se2', 'li1', 2, 8, 65.0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 8, migrations = listOf(MIGRATION_7_8))

        // Fold: s1 (with entries) gets exactly one implicit STRAIGHT block; s2 (empty) gets none.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM blocks"))
        assertEquals(
            1L,
            db.long("SELECT COUNT(*) FROM blocks WHERE id = 'block-s1' AND type = 'STRAIGHT' AND sessionId = 's1'"),
        )
        assertEquals(
            1L,
            db.long(
                "SELECT COUNT(*) FROM exercise_entries WHERE id = 'li1' AND blockId = 'block-s1' AND exerciseId = 'bench-press'",
            ),
        )
        assertEquals(2L, db.long("SELECT COUNT(*) FROM set_entries WHERE exerciseEntryId = 'li1'"))

        // Tombstone cutover: s1 stays live; s2's deletedAt is backfilled from its updatedAt.
        assertEquals(null, db.nullableLong("SELECT deletedAt FROM sessions WHERE id = 's1'"))
        assertEquals(900L, db.nullableLong("SELECT deletedAt FROM sessions WHERE id = 's2'"))
        // createdAt backfilled from startedAt; ids preserved.
        assertEquals(1000L, db.long("SELECT createdAt FROM sessions WHERE id = 's1'"))

        // Exercises kept; the new modality column exists and is null (A7 populates it).
        assertEquals(1L, db.long("SELECT COUNT(*) FROM exercises WHERE id = 'bench-press'"))
        assertTrue(db.long("SELECT modality IS NULL FROM exercises WHERE id = 'bench-press'") == 1L)

        db.close()
    }

    /**
     * v8 → v9 is purely additive (nullable ADD COLUMN). Real v8 rows survive untouched, the new
     * training-data columns exist and read back null, and the result validates against `9.json`
     * (`runMigrationsAndValidate` throws on any schema drift).
     */
    @Test
    fun v9_adds_nullable_training_columns_and_preserves_rows() = runTest {
        // A distinct DB file — the shared `helper` file is left at v8 by the test above, and
        // MigrationTestHelper refuses to createDatabase over an existing file.
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v9.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(version = 8).apply {
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, notes, isTemplate, source, " +
                    "templateId, updatedAt, syncStatus, createdAt, deletedAt) VALUES " +
                    "('s1', 1000, 'Push', 'STRENGTH', NULL, 1, 'MANUAL', NULL, 2000, 'SYNCED', 1000, NULL)",
            )
            execSQL(
                "INSERT INTO blocks (id, sessionId, type, orderIndex, rounds, restBetweenRoundsMs, " +
                    "label, createdAt, updatedAt, deletedAt) VALUES " +
                    "('b1', 's1', 'STRAIGHT', 0, 1, NULL, 'Main', 1000, 2000, NULL)",
            )
            execSQL(
                "INSERT INTO exercise_entries (id, blockId, exerciseId, orderIndex, targetSets, " +
                    "restMs, createdAt, updatedAt, deletedAt) VALUES " +
                    "('e1', 'b1', 'bench-press', 0, 3, NULL, 1000, 2000, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 9, migrations = listOf(MIGRATION_8_9))

        // Existing rows preserved.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM blocks WHERE id = 'b1' AND sessionId = 's1'"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM exercise_entries WHERE id = 'e1' AND blockId = 'b1'"))

        // New columns exist and default to null on migrated rows.
        assertTrue(
            db.long(
                "SELECT section IS NULL AND conditioningFormat IS NULL AND capSeconds IS NULL AND workSeconds IS NULL FROM blocks WHERE id = 'b1'",
            ) ==
                1L,
        )
        assertTrue(db.long("SELECT note IS NULL AND eachSide IS NULL FROM exercise_entries WHERE id = 'e1'") == 1L)
        assertTrue(
            db.long(
                "SELECT category IS NULL AND focus IS NULL AND programWeek IS NULL FROM sessions WHERE id = 's1'",
            ) ==
                1L,
        )

        db.close()
    }

    /**
     * v9 → v10 adds the nullable `sessions.finishedAt` column and the three HYROX reference tables.
     * A real v9 session survives with `finishedAt` null, the new tables exist and are empty, and the
     * result validates against `10.json`.
     */
    @Test
    fun v10_adds_finishedAt_and_hyrox_reference_tables() = runTest {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v10.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(version = 9).apply {
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, notes, isTemplate, source, " +
                    "templateId, category, focus, programWeek, updatedAt, syncStatus, createdAt, deletedAt) VALUES " +
                    "('s1', 1000, 'Hyrox', 'HYROX', NULL, 0, 'FROM_TEMPLATE', 'full-hyrox-simulation', " +
                    "NULL, NULL, NULL, 2000, 'SYNCED', 1000, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 10, migrations = listOf(MIGRATION_9_10))

        // Existing row preserved; the new finishedAt column exists and is null.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sessions WHERE id = 's1'"))
        assertTrue(db.long("SELECT finishedAt IS NULL FROM sessions WHERE id = 's1'") == 1L)

        // New reference tables exist and start empty (seeding is a runtime concern, not the migration).
        assertEquals(0L, db.long("SELECT COUNT(*) FROM hyrox_stations"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM hyrox_divisions"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM hyrox_station_loads"))

        db.close()
    }

    /**
     * v10 → v11 adds the two device-local single-row tables (`athlete_profile`, `entitlement`) for
     * MindSet v1. A real v10 session survives untouched, both new tables exist and start empty (the
     * repos map an absent row to defaults), and the result validates against `11.json`.
     */
    @Test
    fun v11_adds_athlete_profile_and_entitlement_tables() = runTest {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v11.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(version = 10).apply {
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, isTemplate, source, updatedAt, " +
                    "syncStatus, createdAt) VALUES " +
                    "('s1', 1000, 'Hyrox', 'HYROX', 0, 'MANUAL', 2000, 'SYNCED', 1000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 11, migrations = listOf(MIGRATION_10_11))

        // Existing row preserved across the additive migration.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sessions WHERE id = 's1'"))
        // New single-row tables exist and start empty (no seed row — repos default an absent row).
        assertEquals(0L, db.long("SELECT COUNT(*) FROM athlete_profile"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM entitlement"))

        db.close()
    }

    /**
     * v11 → v12 (iteration 3). The load-bearing case: an existing athlete's race intent (stored on
     * `athlete_profile`) must survive as a first-class `race_goal` row *before* the profile is slimmed.
     * Also asserts the reference plane generalized (`hyrox_*` dropped, empty `event_*` created), the
     * additive columns exist, and no session/PR data is lost. Validates against `12.json`.
     */
    @Test
    fun v12_generalizes_events_and_migrates_race_intent_to_goal() = runTest {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v12.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        val futureRaceDate = 4_102_444_800_000L // year 2100 — comfortably ahead of strftime('now')
        helper.createDatabase(version = 11).apply {
            // An onboarded athlete with a target race on the (soon-to-be-removed) profile columns.
            execSQL(
                "INSERT INTO athlete_profile (id, fullName, bodyweightKg, heightCm, defaultDivision, " +
                    "raceDate, raceFormat, raceCity, onboardingComplete) VALUES " +
                    "(0, 'Alex', 80.0, 180.0, 'MEN', $futureRaceDate, 'SINGLES', 'Mumbai', 1)",
            )
            // A logged session + a cached PB — must survive the additive changes untouched.
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, isTemplate, source, updatedAt, " +
                    "syncStatus, createdAt) VALUES " +
                    "('s1', 1000, 'Hyrox', 'HYROX', 0, 'MANUAL', 2000, 'SYNCED', 1000)",
            )
            execSQL(
                "INSERT INTO personal_records (id, exerciseId, kind, value, distanceBucketM, achievedAt, " +
                    "sourceSetId, createdAt, updatedAt, deletedAt) VALUES " +
                    "('pr1', 'hyrox-sled-push', 'BEST_TIME', 135.0, 50, 1500, 'set1', 1000, 1500, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 12, migrations = listOf(MIGRATION_11_12))

        // Race intent migrated into a first-class goal, keyed off the old profile columns.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM race_goal"))
        assertEquals("goal-migrated-0", db.text("SELECT id FROM race_goal"))
        assertEquals("HYROX", db.text("SELECT formatKey FROM race_goal"))
        assertEquals("MEN", db.text("SELECT divisionKey FROM race_goal"))
        assertEquals("SINGLES", db.text("SELECT mode FROM race_goal"))
        assertEquals("Mumbai", db.text("SELECT city FROM race_goal"))
        assertEquals(futureRaceDate, db.long("SELECT targetDate FROM race_goal"))
        // Future race date ⇒ UPCOMING (the CASE branch); envelope + local syncStatus populated.
        assertEquals("UPCOMING", db.text("SELECT status FROM race_goal"))
        assertEquals("PENDING", db.text("SELECT syncStatus FROM race_goal"))

        // Profile slimmed to identity + baseline: race intent now lives only in `race_goal` (asserted
        // above), and onboardingComplete moved to the Preferences DataStore. Identity survives.
        assertEquals("Alex", db.text("SELECT fullName FROM athlete_profile WHERE id = 0"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM pragma_table_info('athlete_profile') WHERE name = 'heightCm'"))
        assertEquals(
            0L,
            db.long(
                "SELECT COUNT(*) FROM pragma_table_info('athlete_profile') " +
                    "WHERE name IN ('defaultDivision', 'defaultDivisionKey', 'raceDate', 'raceCity', 'onboardingComplete')",
            ),
        )

        // Reference plane generalized: hyrox_* dropped, event_* created and empty (runtime seeds them).
        assertEquals(0L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='hyrox_stations'"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='event_format'"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM event_segment"))

        // No data loss on the additive changes; new columns read back null on migrated rows.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sessions WHERE id = 's1'"))
        assertTrue(
            db.long(
                "SELECT raceGoalId IS NULL AND formatKey IS NULL AND divisionKey IS NULL FROM sessions WHERE id = 's1'",
            ) ==
                1L,
        )
        assertEquals(1L, db.long("SELECT COUNT(*) FROM personal_records WHERE id = 'pr1'"))
        assertTrue(db.long("SELECT divisionKey IS NULL FROM personal_records WHERE id = 'pr1'") == 1L)

        db.close()
    }

    /**
     * v12 → v13. Device-local settings leave Room for the Preferences DataStore: the singleton
     * `preferences` table is dropped and `athlete_profile` is recreated to match the slimmed
     * [AthleteProfileEntity]. Room validates the post-migration schema column-for-column, so this is
     * the case that catches a `CREATE TABLE` in [MIGRATION_12_13] drifting from `13.json` — the exact
     * failure mode that would brick every upgrading device on first launch.
     */
    @Test
    fun v13_drops_preferences_and_slims_profile() = runTest {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v13.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(version = 12).apply {
            execSQL("INSERT INTO athlete_profile (id, fullName, bodyweightKg, heightCm) VALUES (0, 'Alex', 80.0, 180.0)")
            execSQL("INSERT INTO preferences (id, weightUnit, themeMode) VALUES (0, 'KG', 'DARK')")
            // A logged session must be untouched by a settings-only migration.
            execSQL(
                "INSERT INTO sessions (id, startedAt, name, type, isTemplate, source, updatedAt, " +
                    "syncStatus, createdAt) VALUES " +
                    "('s1', 1000, 'Hyrox', 'HYROX', 0, 'MANUAL', 2000, 'SYNCED', 1000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 13, migrations = listOf(MIGRATION_12_13))

        // preferences is gone — weightUnit/themeMode now live in the DataStore.
        assertEquals(0L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='preferences'"))

        // Profile survives the recreate with identity + baseline intact, and nothing else.
        assertEquals("Alex", db.text("SELECT fullName FROM athlete_profile WHERE id = 0"))
        assertEquals(4L, db.long("SELECT COUNT(*) FROM pragma_table_info('athlete_profile')"))

        // Session data is untouched.
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sessions WHERE id = 's1'"))

        db.close()
    }

    /**
     * v13 → v14. Adds the `race_event` reference table behind the onboarding race picker. A pure
     * addition, so nothing is recreated and no data moves — but `runMigrationsAndValidate` still
     * checks the `CREATE TABLE` and both indices against `14.json`, which is what catches the
     * schema drift that would otherwise brick every upgrading device on first launch.
     */
    @Test
    fun v14_adds_the_race_event_calendar_table() = runTest {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            fileName = NSTemporaryDirectory() + "mindset-migration-test-v14.db",
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(version = 13).apply {
            execSQL("INSERT INTO athlete_profile (id, fullName, bodyweightKg, heightCm) VALUES (0, 'Alex', 80.0, 180.0)")
            execSQL(
                "INSERT INTO race_goal (id, formatKey, divisionKey, mode, targetDate, city, status, " +
                    "createdAt, updatedAt, syncStatus) VALUES " +
                    "('g1', 'HYROX', 'MEN', 'SINGLES', 5000, 'Rome', 'UPCOMING', 1000, 1000, 'PENDING')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(version = 14, migrations = listOf(MIGRATION_13_14))

        // The new table exists and starts empty — it is filled by the repository's seed-on-read.
        assertEquals(0L, db.long("SELECT COUNT(*) FROM race_event"))
        assertEquals(7L, db.long("SELECT COUNT(*) FROM pragma_table_info('race_event')"))

        // Existing race intent is untouched by a pure-addition migration.
        assertEquals("Rome", db.text("SELECT city FROM race_goal WHERE id = 'g1'"))
        assertEquals("Alex", db.text("SELECT fullName FROM athlete_profile WHERE id = 0"))

        db.close()
    }
}
