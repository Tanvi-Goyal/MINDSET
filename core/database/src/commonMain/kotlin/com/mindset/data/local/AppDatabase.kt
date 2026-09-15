package com.mindset.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.mindset.data.local.dao.AthleteProfileDao
import com.mindset.data.local.dao.BlockDao
import com.mindset.data.local.dao.EntitlementDao
import com.mindset.data.local.dao.EventDivisionDao
import com.mindset.data.local.dao.EventFormatDao
import com.mindset.data.local.dao.EventSegmentDao
import com.mindset.data.local.dao.EventSegmentStandardDao
import com.mindset.data.local.dao.ExerciseDao
import com.mindset.data.local.dao.ExerciseEntryDao
import com.mindset.data.local.dao.OutboxDao
import com.mindset.data.local.dao.PersonalRecordDao
import com.mindset.data.local.dao.PlannedSessionDao
import com.mindset.data.local.dao.RaceEventDao
import com.mindset.data.local.dao.RaceGoalDao
import com.mindset.data.local.dao.SessionDao
import com.mindset.data.local.dao.SetEntryDao
import com.mindset.data.local.dao.StatsDao
import com.mindset.data.local.dao.SyncMetaDao

/**
 * The Room database — the single source of truth the whole app reads through.
 *
 * [AppDatabaseConstructor] is an `expect` object because Room cannot use reflection to
 * instantiate the generated implementation on Kotlin/Native (iOS). KSP fills in the `actual`
 * per target, so each platform gets a concrete constructor without any reflective lookup.
 */
@Database(
    entities = [
        SessionEntity::class, OutboxEntry::class, SyncMeta::class, PlannedSession::class,
        Exercise::class, ExerciseEntryEntity::class, SetEntryEntity::class,
        BlockEntity::class, PersonalRecord::class,
        AthleteProfileEntity::class, EntitlementEntity::class,
        EventFormatEntity::class, EventSegmentEntity::class, EventDivisionEntity::class,
        EventSegmentStandardEntity::class, RaceGoalEntity::class, RaceEventEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncMetaDao(): SyncMetaDao

    abstract fun plannedSessionDao(): PlannedSessionDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun exerciseEntryDao(): ExerciseEntryDao

    abstract fun setEntryDao(): SetEntryDao

    abstract fun statsDao(): StatsDao

    abstract fun blockDao(): BlockDao

    abstract fun personalRecordDao(): PersonalRecordDao

    abstract fun eventFormatDao(): EventFormatDao

    abstract fun eventDivisionDao(): EventDivisionDao

    abstract fun eventSegmentDao(): EventSegmentDao

    abstract fun eventSegmentStandardDao(): EventSegmentStandardDao

    abstract fun raceGoalDao(): RaceGoalDao

    abstract fun raceEventDao(): RaceEventDao

    abstract fun athleteProfileDao(): AthleteProfileDao

    abstract fun entitlementDao(): EntitlementDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
