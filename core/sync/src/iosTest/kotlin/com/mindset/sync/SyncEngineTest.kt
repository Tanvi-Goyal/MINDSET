package com.mindset.sync

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mindset.common.UuidV7Generator
import com.mindset.contracts.BlockDto
import com.mindset.contracts.ExerciseEntryDto
import com.mindset.contracts.PullResponse
import com.mindset.contracts.PushResponse
import com.mindset.contracts.SessionDto
import com.mindset.contracts.SetDto
import com.mindset.data.SessionRepositoryImpl
import com.mindset.data.emptyExerciseAssetReader
import com.mindset.data.local.AppDatabase
import com.mindset.data.local.SessionEntity
import com.mindset.data.local.SessionType
import com.mindset.data.local.SyncStatus
import com.mindset.data.remote.SyncApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the common [SyncEngine] against a fake [SyncApi] (no live server). Lives in iosTest
 * because the no-arg in-memory Room builder is Context-free on native (same reason as the Phase 0
 * repository test).
 */
class SyncEngineTest {

    private lateinit var database: AppDatabase

    /** Fake transport: records what was pushed, returns a scripted pull payload. */
    private class FakeSyncApi(var pullChanges: List<SessionDto> = emptyList(), var nextCursor: Long = 0) : SyncApi {
        val pushed = mutableListOf<SessionDto>()
        override suspend fun push(changes: List<SessionDto>): PushResponse {
            pushed += changes
            return PushResponse(accepted = changes.size)
        }
        override suspend fun pull(cursor: Long?): PullResponse = PullResponse(changes = pullChanges, nextCursor = nextCursor)
    }

    private fun engineWith(api: SyncApi) = SyncEngine(
        database = database,
        sessionDao = database.sessionDao(),
        outboxDao = database.outboxDao(),
        syncMetaDao = database.syncMetaDao(),
        api = api,
    )

    /** Repository under test, wired with the real UUIDv7 + system-clock seam. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun repo() = SessionRepositoryImpl(
        database,
        emptyExerciseAssetReader,
        UuidV7Generator(kotlin.time.Clock.System),
        kotlin.time.Clock.System,
    )

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun teardown() = database.close()

    @Test
    fun push_drains_outbox_and_marks_session_synced() = runTest {
        val repo = repo()
        val created = repo.createSession(SessionType.STRENGTH) // enqueues an outbox row
        assertEquals(1, database.outboxDao().count())

        val api = FakeSyncApi()
        engineWith(api).sync()

        assertEquals(1, api.pushed.size, "the pending session should have been pushed")
        assertEquals(created.id, api.pushed.first().id)
        assertEquals(0, database.outboxDao().count(), "outbox should be drained on success")
        assertEquals(SyncStatus.SYNCED, database.sessionDao().getById(created.id)?.syncStatus)
    }

    @Test
    fun pull_applies_newer_remote_row_and_ignores_stale_one() = runTest {
        val local = SessionEntity(id = "s1", startedAt = 1, updatedAt = 100, syncStatus = SyncStatus.SYNCED)
        database.sessionDao().upsert(local)

        // Remote sends a NEWER version (updatedAt 200) → should win.
        engineWith(FakeSyncApi(pullChanges = listOf(dto("s1", updatedAt = 200)), nextCursor = 1)).sync()
        assertEquals(200, database.sessionDao().getById("s1")?.updatedAt, "newer remote should apply")

        // Remote sends an OLDER version (updatedAt 50) → local (200) must be kept.
        engineWith(FakeSyncApi(pullChanges = listOf(dto("s1", updatedAt = 50)), nextCursor = 2)).sync()
        assertEquals(200, database.sessionDao().getById("s1")?.updatedAt, "stale remote must be ignored (LWW)")
    }

    @Test
    fun pulled_soft_delete_hides_row_from_the_observed_list() = runTest {
        // Finished, because observeAll() hides empty in-progress sessions.
        val local = SessionEntity(id = "s1", startedAt = 1, finishedAt = 2, updatedAt = 100, syncStatus = SyncStatus.SYNCED)
        database.sessionDao().upsert(local)
        assertEquals(1, database.sessionDao().observeAll().first().size)

        engineWith(
            FakeSyncApi(pullChanges = listOf(dto("s1", updatedAt = 300, deletedAt = 300)), nextCursor = 1),
        ).sync()

        assertTrue(
            database.sessionDao().observeAll().first().isEmpty(),
            "a pulled soft-delete should drop the row from the UI-facing query",
        )
    }

    @Test
    fun session_name_and_type_round_trip_through_push_and_pull() = runTest {
        val repo = repo()
        val plan = com.mindset.model.PlannedSession(
            id = "p1",
            name = "Leg Day",
            type = com.mindset.model.SessionType.CONDITIONING,
            targetDurationMin = 40,
            focus = "Pull focus",
        )
        val created = repo.startPlannedSession(plan)

        val api = FakeSyncApi()
        engineWith(api).sync()

        // Push carries name/type over the wire.
        assertEquals("Leg Day", api.pushed.first().name)
        assertEquals(com.mindset.data.local.SessionType.CONDITIONING, api.pushed.first().type)

        // Pull writes a remote name/type into the local entity.
        engineWith(
            FakeSyncApi(
                pullChanges = listOf(
                    SessionDto(id = "s2", startedAt = 1, name = "Hyrox Sim", type = "HYROX", updatedAt = 500),
                ),
                nextCursor = 5,
            ),
        ).sync()
        val pulled = database.sessionDao().getById("s2")
        assertEquals("Hyrox Sim", pulled?.name)
        assertEquals("HYROX", pulled?.type)
    }

    @Test
    fun logged_sets_ride_with_the_session_through_nested_push_and_pull() = runTest {
        val repo = repo()

        // Log a session with one exercise + one set, then push.
        val session = repo.createSession(SessionType.STRENGTH)
        repo.addExercise(session.id, "bench-press")
        val entryId = database.exerciseEntryDao().getBySession(session.id).first().id
        repo.addSet(session.id, entryId, reps = 10, loadKg = 60.0)

        val api = FakeSyncApi()
        engineWith(api).sync()

        // The aggregate DTO carries the block → entry → set tree, entry keeps its real id.
        val pushedEntry = api.pushed.first { it.id == session.id }.blocks.first().entries.first()
        assertEquals(entryId, pushedEntry.id, "entry id is preserved on the wire, not regenerated")
        assertEquals("bench-press", pushedEntry.exerciseId)
        assertEquals(10, pushedEntry.sets.first().reps)
        assertEquals(60.0, pushedEntry.sets.first().loadKg)

        // Pulling a nested remote session materializes its tree with the SAME ids from the wire.
        engineWith(
            FakeSyncApi(
                pullChanges = listOf(
                    SessionDto(
                        id = "remote1",
                        startedAt = 1,
                        updatedAt = 900,
                        blocks = listOf(
                            BlockDto(
                                id = "rb1",
                                entries = listOf(
                                    ExerciseEntryDto(
                                        id = "re1",
                                        exerciseId = "back-squat",
                                        orderIndex = 0,
                                        sets = listOf(SetDto(id = "rs1", setNumber = 1, reps = 5, loadKg = 100.0)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                nextCursor = 9,
            ),
        ).sync()

        val remoteEntries = database.exerciseEntryDao().getBySession("remote1")
        assertEquals(1, remoteEntries.size)
        assertEquals("re1", remoteEntries.first().id, "child id comes from the wire, not minted locally")
        assertEquals("back-squat", remoteEntries.first().exerciseId)
        val remoteSets = database.setEntryDao().getForEntry("re1")
        assertEquals(1, remoteSets.size)
        assertEquals("rs1", remoteSets.first().id)
        assertEquals(100.0, remoteSets.first().loadKg)
    }

    @Test
    fun template_and_its_targets_ride_through_push_and_pull() = runTest {
        val repo = repo()

        // A template with one exercise and one target-only (prescription) set.
        val template = repo.createTemplate(name = "Upper A", type = SessionType.STRENGTH)
        repo.addExercise(template.id, "bench-press")
        val itemId = database.exerciseEntryDao().getBySession(template.id).first().id
        repo.addTargetSet(template.id, itemId, reps = 5, loadKg = 100.0)

        val api = FakeSyncApi()
        engineWith(api).sync()

        // Push carries isTemplate + the target metrics; actuals are absent.
        val pushed = api.pushed.first { it.id == template.id }
        assertTrue(pushed.isTemplate, "template flag crosses the wire")
        assertEquals("MANUAL", pushed.source)
        val pushedSet = pushed.blocks.first().entries.first().sets.first()
        assertEquals(5, pushedSet.targetReps)
        assertEquals(100.0, pushedSet.targetLoadKg)
        assertNull(pushedSet.reps, "a template set has no actuals")

        // Pulling a remote template materializes it locally as a template with its targets intact.
        engineWith(
            FakeSyncApi(
                pullChanges = listOf(
                    SessionDto(
                        id = "remoteT",
                        startedAt = 1,
                        name = "Remote Tmpl",
                        updatedAt = 700,
                        isTemplate = true,
                        source = "MANUAL",
                        blocks = listOf(
                            BlockDto(
                                id = "tb1",
                                entries = listOf(
                                    ExerciseEntryDto(
                                        id = "te1",
                                        exerciseId = "back-squat",
                                        orderIndex = 0,
                                        sets = listOf(
                                            SetDto(id = "ts1", setNumber = 1, targetReps = 3, targetLoadKg = 140.0),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                nextCursor = 7,
            ),
        ).sync()

        val pulled = database.sessionDao().getById("remoteT")
        assertTrue(pulled!!.isTemplate)
        val pulledItem = database.exerciseEntryDao().getBySession("remoteT").first()
        val pulledSet = database.setEntryDao().getForEntry(pulledItem.id).first()
        assertEquals(3, pulledSet.targetReps)
        assertEquals(140.0, pulledSet.targetLoadKg)
        assertNull(pulledSet.reps)
    }

    private fun dto(id: String, updatedAt: Long, deletedAt: Long? = null) =
        SessionDto(id = id, startedAt = 1, notes = null, updatedAt = updatedAt, deletedAt = deletedAt)
}
