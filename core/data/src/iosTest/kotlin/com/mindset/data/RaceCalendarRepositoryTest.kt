@file:OptIn(ExperimentalTime::class)

package com.mindset.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mindset.contracts.HyroxCalendarSeed
import com.mindset.contracts.RaceCalendarResponse
import com.mindset.contracts.isoDateToUtcMillis
import com.mindset.data.local.AppDatabase
import com.mindset.data.remote.RaceCalendarApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The race calendar's defining property: the picker is never empty, whatever the network does.
 * Onboarding is the first screen a user ever sees, so these cases are the feature working at all.
 */
class RaceCalendarRepositoryTest {
    private lateinit var database: AppDatabase

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun teardown() = database.close()

    private fun repoWith(api: RaceCalendarApi, today: String = "2026-09-01") =
        RaceCalendarRepositoryImpl(database, api, fixedClockAt(today))

    @Test
    fun seeds_the_bundled_calendar_when_the_server_cannot_be_reached() = runTest {
        val cities = repoWith(FakeRaceCalendarApi(response = null)).observeCities().first()

        assertTrue(cities.isNotEmpty(), "an unreachable server must still leave a usable picker")
        assertTrue(cities.any { it.city == "Rome" }, "expected the bundled calendar, got $cities")
    }

    @Test
    fun an_empty_fetch_leaves_the_seeded_calendar_intact() = runTest {
        val repo = repoWith(FakeRaceCalendarApi(RaceCalendarResponse(events = emptyList(), version = 9)))
        repo.refresh()

        assertTrue(
            repo.observeCities().first().isNotEmpty(),
            "an empty payload must never wipe the calendar out from under the picker",
        )
    }

    @Test
    fun remote_rows_replace_the_seed_row_they_share_an_id_with() = runTest {
        val romeId = "hyrox-rome-2026-09-23"
        assertTrue(HyroxCalendarSeed.events.any { it.id == romeId }, "seed id changed; update this test")

        val corrected = HyroxCalendarSeed.events
            .first { it.id == romeId }
            .copy(city = "Roma")
        val repo = repoWith(FakeRaceCalendarApi(RaceCalendarResponse(events = listOf(corrected), version = 2)))

        repo.refresh()
        val cities = repoWith(FakeRaceCalendarApi(response = null)).observeCities().first()

        assertTrue(cities.any { it.city == "Roma" }, "the remote correction should win")
        assertFalse(cities.any { it.city == "Rome" }, "the superseded seed row should be gone")
        assertTrue(cities.any { it.city == "Oslo" }, "seed rows the server didn't mention must survive")
    }

    @Test
    fun a_catalog_older_than_the_one_already_applied_is_ignored() = runTest {
        val romeId = "hyrox-rome-2026-09-23"
        val rome = HyroxCalendarSeed.events.first { it.id == romeId }
        val api = FakeRaceCalendarApi(RaceCalendarResponse(events = listOf(rome.copy(city = "Roma")), version = 5))
        val repo = repoWith(api)
        repo.refresh()

        // A server rolled back to an older catalog must not undo what we already hold.
        api.response = RaceCalendarResponse(events = listOf(rome.copy(city = "Stale")), version = 3)
        repo.refresh()

        val cities = repo.observeCities().first().map { it.city }
        assertTrue(cities.contains("Roma"), "the newer catalog should still stand, got $cities")
        assertFalse(cities.contains("Stale"), "an older catalog version must be ignored")
        assertEquals(2, api.calls, "refresh still asks every time; it just declines to apply")
    }

    @Test
    fun races_that_have_already_finished_are_not_offered() = runTest {
        val repo = repoWith(FakeRaceCalendarApi(response = null), today = "2027-06-01")

        assertTrue(
            repo.observeCities().first().isEmpty(),
            "every seeded 2026 race is over by mid-2027 and must drop out of the picker",
        )
    }

    @Test
    fun a_city_exposes_every_day_of_its_race_window() = runTest {
        val repo = repoWith(FakeRaceCalendarApi(response = null))
        val rome = repo.observeCities().first().first { it.city == "Rome" }

        val events = repo.observeEventsIn(rome).first()
        assertEquals(1, events.size)
        // Rome runs 23–27 September: five days of heats to choose between.
        assertEquals(5, events.single().raceDays.size)
        assertTrue(events.single().isMultiDay)
    }
}

private class FakeRaceCalendarApi(var response: RaceCalendarResponse?) : RaceCalendarApi {
    var calls = 0
        private set

    override suspend fun calendar(): RaceCalendarResponse? {
        calls += 1
        return response
    }
}

private fun fixedClockAt(iso: String): Clock = object : Clock {
    private val instant = Instant.fromEpochMilliseconds(isoDateToUtcMillis(iso))

    override fun now(): Instant = instant
}
