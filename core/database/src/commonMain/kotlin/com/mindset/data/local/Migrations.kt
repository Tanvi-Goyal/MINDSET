package com.mindset.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v7 → v8. The pre-rethink 3-level tree (session → logged_item → set) becomes the 4-level model
 * (session → block → exercise_entry → set); sync tombstones (`deletedAt`) replace the boolean
 * `deleted`; the PB cache and modality columns arrive.
 *
 * **Why table-recreate, not `ALTER TABLE ADD COLUMN`, for `sessions`/`set_entries`:** SQLite can
 * only add a NOT NULL column *with a DEFAULT*, but Room's generated schema declares no default, so
 * Room's post-migration schema validation would reject the mismatch. Recreating each changed table
 * from Room's exact `createSql` (copied verbatim from `schemas/8.json`) sidesteps that — the result
 * validates column-for-column. `exercises` only gains NULLABLE columns, so plain ADD COLUMN is fine.
 *
 * **Legacy fold:** each `logged_item` becomes an `exercise_entry` under one implicit STRAIGHT block
 * per session (id `block-<sessionId>`), matching the runtime convention in `Mappers`/repo. Child
 * envelope columns backfill from the parent session (`createdAt = startedAt`, tombstone from the old
 * `deleted`). Client-generated PKs are **preserved, never regenerated** — they are the sync identity.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 1. sessions → recreate: + createdAt (backfilled from startedAt), + deletedAt (from `deleted`),
        //    − deleted.
        connection.execSQL(
            "CREATE TABLE `sessions_new` (`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `notes` TEXT, `isTemplate` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, `templateId` TEXT, `updatedAt` INTEGER NOT NULL, " +
                "`syncStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "INSERT INTO `sessions_new` (id, startedAt, name, type, notes, isTemplate, source, " +
                "templateId, updatedAt, syncStatus, createdAt, deletedAt) " +
                "SELECT id, startedAt, name, type, notes, isTemplate, source, templateId, updatedAt, " +
                "syncStatus, startedAt, CASE WHEN deleted = 1 THEN updatedAt ELSE NULL END FROM `sessions`",
        )
        connection.execSQL("DROP TABLE `sessions`")
        connection.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`")

        // 2. blocks → new; one implicit STRAIGHT block per session that actually has entries.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocks` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `rounds` INTEGER, " +
                "`restBetweenRoundsMs` INTEGER, `label` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_blocks_sessionId` ON `blocks` (`sessionId`)")
        connection.execSQL(
            "INSERT INTO `blocks` (id, sessionId, type, orderIndex, rounds, restBetweenRoundsMs, " +
                "label, createdAt, updatedAt, deletedAt) " +
                "SELECT 'block-' || s.id, s.id, 'STRAIGHT', 0, 1, NULL, NULL, s.createdAt, s.updatedAt, NULL " +
                "FROM `sessions` s WHERE EXISTS (SELECT 1 FROM `logged_items` li WHERE li.sessionId = s.id)",
        )

        // 3. exercise_entries → from logged_items, reparented to the implicit block.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_entries` (`id` TEXT NOT NULL, `blockId` TEXT NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `targetSets` INTEGER, " +
                "`restMs` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entries_blockId` ON `exercise_entries` (`blockId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entries_exerciseId` ON `exercise_entries` (`exerciseId`)")
        connection.execSQL(
            "INSERT INTO `exercise_entries` (id, blockId, exerciseId, orderIndex, targetSets, restMs, " +
                "createdAt, updatedAt, deletedAt) " +
                "SELECT li.id, 'block-' || li.sessionId, li.exerciseId, li.orderIndex, NULL, NULL, " +
                "s.createdAt, s.updatedAt, NULL FROM `logged_items` li JOIN `sessions` s ON s.id = li.sessionId",
        )
        connection.execSQL("DROP TABLE `logged_items`")

        // 4. set_entries → recreate: loggedItemId → exerciseEntryId, + calories/targetCalories + envelope.
        connection.execSQL(
            "CREATE TABLE `set_entries_new` (`id` TEXT NOT NULL, `exerciseEntryId` TEXT NOT NULL, " +
                "`setNumber` INTEGER NOT NULL, `reps` INTEGER, `loadKg` REAL, `timeSec` INTEGER, " +
                "`distanceM` INTEGER, `rpe` INTEGER, `targetReps` INTEGER, `targetLoadKg` REAL, " +
                "`targetTimeSec` INTEGER, `targetDistanceM` INTEGER, `calories` INTEGER, " +
                "`targetCalories` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "INSERT INTO `set_entries_new` (id, exerciseEntryId, setNumber, reps, loadKg, timeSec, " +
                "distanceM, rpe, targetReps, targetLoadKg, targetTimeSec, targetDistanceM, calories, " +
                "targetCalories, createdAt, updatedAt, deletedAt) " +
                "SELECT id, loggedItemId, setNumber, reps, loadKg, timeSec, distanceM, rpe, targetReps, " +
                "targetLoadKg, targetTimeSec, targetDistanceM, NULL, NULL, 0, 0, NULL FROM `set_entries`",
        )
        connection.execSQL("DROP TABLE `set_entries`")
        connection.execSQL("ALTER TABLE `set_entries_new` RENAME TO `set_entries`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_set_entries_exerciseEntryId` ON `set_entries` (`exerciseEntryId`)")

        // 5. exercises → add nullable modality columns + indices (rows kept; A7 populates values).
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `modality` TEXT")
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `defaultMetric` TEXT")
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `hyroxStation` TEXT")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_modality` ON `exercises` (`modality`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_hyroxStation` ON `exercises` (`hyroxStation`)")

        // 6. personal_records → new, empty (recomputable from history later).
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `personal_records` (`id` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `value` REAL NOT NULL, `distanceBucketM` INTEGER, " +
                "`achievedAt` INTEGER NOT NULL, `sourceSetId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_personal_records_exerciseId` ON `personal_records` (`exerciseId`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_records_exerciseId_kind_distanceBucketM` ON `personal_records` (`exerciseId`, `kind`, `distanceBucketM`)",
        )
    }
}

/**
 * v8 → v9. Adds the training-data-model columns needed to represent real programs: conditioning
 * shape (`conditioningFormat`/`capSeconds`/`workSeconds`) and section grouping (`section`) on blocks;
 * a coaching `note` + per-side `eachSide` flag on entries; and lightweight template metadata
 * (`category`/`focus`/`programWeek`) on sessions.
 *
 * **Purely additive, all NULLABLE.** SQLite rejects a NOT-NULL added column without a DEFAULT, and
 * Room's generated schema declares no default (see [MIGRATION_7_8]) — so every new column is nullable
 * and this is plain `ALTER TABLE ADD COLUMN`, no table-recreate, no data touched.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `blocks` ADD COLUMN `section` TEXT")
        connection.execSQL("ALTER TABLE `blocks` ADD COLUMN `conditioningFormat` TEXT")
        connection.execSQL("ALTER TABLE `blocks` ADD COLUMN `capSeconds` INTEGER")
        connection.execSQL("ALTER TABLE `blocks` ADD COLUMN `workSeconds` INTEGER")
        connection.execSQL("ALTER TABLE `exercise_entries` ADD COLUMN `note` TEXT")
        connection.execSQL("ALTER TABLE `exercise_entries` ADD COLUMN `eachSide` INTEGER")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `category` TEXT")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `focus` TEXT")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `programWeek` INTEGER")
    }
}

/**
 * v9 → v10. Adds the HYROX reference tables (the race format as DB-seeded data, replacing the in-code
 * `HyroxStandards`) and a nullable `finishedAt` timestamp on sessions (stamped when a live workout is
 * completed; enables total-time display + a "completed" notion).
 *
 * The three new `CREATE TABLE`s are copied verbatim from Room's generated `schemas/10.json` shape so
 * post-migration validation passes column-for-column; `finishedAt` is a plain nullable `ADD COLUMN`
 * (a NOT-NULL add without a DEFAULT would fail validation — see [MIGRATION_7_8]).
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `finishedAt` INTEGER")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hyrox_stations` (`id` TEXT NOT NULL, `number` INTEGER NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, `name` TEXT NOT NULL, `blockLabel` TEXT NOT NULL, " +
                "`runBeforeLabel` TEXT NOT NULL, `metric` TEXT NOT NULL, `distanceM` INTEGER, `reps` INTEGER, " +
                "`descriptor` TEXT NOT NULL, `loadType` TEXT, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hyrox_divisions` (`key` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, `wallBallKg` INTEGER NOT NULL, `wallTargetM` TEXT NOT NULL, " +
                "PRIMARY KEY(`key`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hyrox_station_loads` (`id` TEXT NOT NULL, `divisionKey` TEXT NOT NULL, " +
                "`loadType` TEXT NOT NULL, `weightDisplay` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hyrox_station_loads_divisionKey` ON `hyrox_station_loads` (`divisionKey`)",
        )
    }
}

/**
 * v10 → v11. Adds two device-local single-row tables for MindSet v1: `athlete_profile` (profile + target
 * race captured in Onboarding) and `entitlement` (the subscription stub). Both are brand-new tables, so
 * they are created fresh from Room's generated `schemas/11.json` shape (verbatim) — no data to migrate,
 * no seed row (the repos map an absent row to defaults, like `preferences`). NOT-NULL columns need no
 * DEFAULT here because a fresh CREATE TABLE inserts no rows (contrast the ADD COLUMN rule in [MIGRATION_7_8]).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `athlete_profile` (`id` INTEGER NOT NULL, `fullName` TEXT NOT NULL, " +
                "`bodyweightKg` REAL, `heightCm` REAL, `defaultDivision` TEXT NOT NULL, `raceDate` INTEGER, " +
                "`raceFormat` TEXT, `raceCity` TEXT, `onboardingComplete` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entitlement` (`id` INTEGER NOT NULL, `isPro` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v11 → v12 (iteration 3). Generalizes the reference plane past Hyrox and makes the goal race
 * first-class:
 *  - the Hyrox-specific `hyrox_*` reference tables become format-agnostic `event_*` tables (Hyrox is
 *    reseeded at runtime as data; a new event is then pure seed rows, never a migration);
 *  - a new syncable `race_goal` table replaces the single target that lived on `athlete_profile`;
 *  - race-awareness columns land on `sessions` (`raceGoalId`/`formatKey`/`divisionKey` + nullable
 *    summary metrics) and `exercise_entries` (`segmentKey`), plus a weight-class `divisionKey` on
 *    `personal_records`;
 *  - `athlete_profile` slims to identity + baseline.
 *
 * All new `CREATE TABLE`s are copied verbatim from Room's generated `schemas/12.json`, so post-migration
 * validation passes column-for-column. New columns are NULLABLE (plain `ADD COLUMN` — a NOT-NULL add
 * without a DEFAULT would fail validation, see [MIGRATION_7_8]).
 *
 * **Order matters:** the existing athlete's race intent is copied INTO `race_goal` (step 6) *before*
 * `athlete_profile` is recreated (step 7) — the recreate drops `raceDate`/`raceFormat`/`raceCity`, so
 * we must read them first. The migrated goal can't call the injected `Clock` (raw SQL), so its
 * timestamps come from SQLite `strftime` wall-clock; its id is deterministic (only one profile row).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 1. Event-format reference tables (empty; runtime ensureSeeded populates them).
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_format` (`formatKey` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, PRIMARY KEY(`formatKey`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_segment` (`id` TEXT NOT NULL, `formatKey` TEXT NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, `kind` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `label` TEXT NOT NULL, `metric` TEXT NOT NULL, `distanceM` INTEGER, " +
                "`reps` INTEGER, `loadType` TEXT, `descriptor` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_event_segment_formatKey` ON `event_segment` (`formatKey`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_division` (`id` TEXT NOT NULL, `formatKey` TEXT NOT NULL, " +
                "`key` TEXT NOT NULL, `label` TEXT NOT NULL, `gender` TEXT NOT NULL, `tier` TEXT NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_event_division_formatKey` ON `event_division` (`formatKey`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `segment_standard` (`id` TEXT NOT NULL, `formatKey` TEXT NOT NULL, " +
                "`divisionKey` TEXT NOT NULL, `segmentId` TEXT NOT NULL, `mode` TEXT NOT NULL, `loadKg` REAL, " +
                "`loadDisplay` TEXT, `targetReps` INTEGER, `targetDistanceM` INTEGER, `targetHeightM` REAL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_segment_standard_formatKey` ON `segment_standard` (`formatKey`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_segment_standard_divisionKey` ON `segment_standard` (`divisionKey`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_segment_standard_segmentId` ON `segment_standard` (`segmentId`)")

        // 2. Drop the old Hyrox-specific reference tables (regenerated as event_* data — no user data).
        connection.execSQL("DROP TABLE IF EXISTS `hyrox_station_loads`")
        connection.execSQL("DROP TABLE IF EXISTS `hyrox_divisions`")
        connection.execSQL("DROP TABLE IF EXISTS `hyrox_stations`")
        connection.execSQL("DELETE FROM `sync_meta` WHERE `key` = 'hyrox_seed_version'")

        // 3. sessions → race-awareness + summary (all nullable ADD COLUMN).
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `raceGoalId` TEXT")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `formatKey` TEXT")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `divisionKey` TEXT")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `avgHeartRate` INTEGER")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `caloriesKcal` INTEGER")
        connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `perceivedEffort` INTEGER")

        // 4. exercise_entries → segment tag (nullable ADD COLUMN).
        connection.execSQL("ALTER TABLE `exercise_entries` ADD COLUMN `segmentKey` TEXT")

        // 5. personal_records → weight-class dimension + recreate the composite index to include it.
        connection.execSQL("ALTER TABLE `personal_records` ADD COLUMN `divisionKey` TEXT")
        connection.execSQL("DROP INDEX IF EXISTS `index_personal_records_exerciseId_kind_distanceBucketM`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_records_exerciseId_kind_divisionKey_distanceBucketM` " +
                "ON `personal_records` (`exerciseId`, `kind`, `divisionKey`, `distanceBucketM`)",
        )

        // 6. race_goal → new table, then migrate an existing user's race intent INTO it (before the
        //    athlete_profile recreate drops those columns). Deterministic id (one profile row);
        //    strftime wall-clock for the envelope; status by whether the race date is still ahead.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `race_goal` (`id` TEXT NOT NULL, `formatKey` TEXT NOT NULL, " +
                "`divisionKey` TEXT NOT NULL, `mode` TEXT NOT NULL, `targetDate` INTEGER, `city` TEXT, " +
                "`goalTimeSec` INTEGER, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_race_goal_status_targetDate` ON `race_goal` (`status`, `targetDate`)",
        )
        connection.execSQL(
            "INSERT INTO `race_goal` (id, formatKey, divisionKey, mode, targetDate, city, goalTimeSec, " +
                "status, createdAt, updatedAt, deletedAt, syncStatus) " +
                "SELECT 'goal-migrated-0', 'HYROX', defaultDivision, COALESCE(raceFormat, 'SINGLES'), " +
                "raceDate, raceCity, NULL, " +
                "CASE WHEN raceDate >= strftime('%s','now') * 1000 THEN 'UPCOMING' ELSE 'COMPLETED' END, " +
                "strftime('%s','now') * 1000, strftime('%s','now') * 1000, NULL, 'PENDING' " +
                "FROM `athlete_profile` WHERE id = 0 AND raceDate IS NOT NULL",
        )

        // 7. athlete_profile → recreate slim: identity + baseline only. Race intent already moved to
        //    `race_goal` in step 6, so the division/mode/race columns have no second home here, and
        //    `onboardingComplete` is a device-local flag that belongs in the Preferences DataStore.
        //    Column removal needs a table-recreate (no DROP COLUMN pre-3.35). This CREATE must match
        //    schemas/…/12.json column-for-column or runMigrationsAndValidate(version = 12) fails.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `athlete_profile_new` (`id` INTEGER NOT NULL, `fullName` TEXT NOT NULL, " +
                "`bodyweightKg` REAL, `heightCm` REAL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "INSERT INTO `athlete_profile_new` (id, fullName, bodyweightKg, heightCm) " +
                "SELECT id, fullName, bodyweightKg, heightCm FROM `athlete_profile`",
        )
        connection.execSQL("DROP TABLE `athlete_profile`")
        connection.execSQL("ALTER TABLE `athlete_profile_new` RENAME TO `athlete_profile`")
    }
}

/**
 * v12 → v13. Device-local settings move out of Room into a Preferences DataStore: the singleton
 * `preferences` table (themeMode/weightUnit) and the `onboardingComplete` flag are no longer
 * persisted by Room. This drops the `preferences` table and recreates `athlete_profile` with only
 * the four columns [AthleteProfileEntity] declares (column removal needs a table-recreate; see
 * [MIGRATION_7_8]).
 *
 * `onboardingComplete` moved to the DataStore. `defaultDivisionKey`/`defaultMode` were already
 * dropped from the entity — race identity lives in the Preferences DataStore now (`setRaceInfo`)
 * and as a first-class `race_goal` row, so the profile no longer carries a duplicate.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 1. preferences table is gone — themeMode/weightUnit now live in the Preferences DataStore.
        connection.execSQL("DROP TABLE IF EXISTS `preferences`")

        // 2. athlete_profile → recreate with exactly the columns AthleteProfileEntity declares.
        //    Room validates the post-migration schema column-for-column, so this CREATE must match
        //    schemas/…/13.json verbatim or the DB fails to open.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `athlete_profile_new` (`id` INTEGER NOT NULL, `fullName` TEXT NOT NULL, " +
                "`bodyweightKg` REAL, `heightCm` REAL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "INSERT INTO `athlete_profile_new` (id, fullName, bodyweightKg, heightCm) " +
                "SELECT id, fullName, bodyweightKg, heightCm FROM `athlete_profile`",
        )
        connection.execSQL("DROP TABLE `athlete_profile`")
        connection.execSQL("ALTER TABLE `athlete_profile_new` RENAME TO `athlete_profile`")
    }
}

/**
 * v13 → v14. Adds `race_event`: the HYROX race calendar the onboarding picker reads.
 *
 * A pure addition, so there is no table-recreate here (see [MIGRATION_7_8] for why existing tables
 * need one) — but the CREATE and both indices must still match `schemas/…/14.json` verbatim, or
 * Room's post-migration validation refuses to open the database.
 *
 * No backfill: the table is populated by the repository's seed-on-read, which runs on the next read
 * because `race_calendar_seed_version` is absent and therefore reads as 0.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `race_event` (`id` TEXT NOT NULL, `city` TEXT NOT NULL, " +
                "`country` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, " +
                "`formatKey` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_race_event_city` ON `race_event` (`city`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_race_event_startDate` ON `race_event` (`startDate`)",
        )
    }
}
