@file:OptIn(ExperimentalTime::class)

package com.mindset.data

import com.mindset.contracts.HyroxCalendarSeed
import com.mindset.contracts.RaceEventDto
import com.mindset.data.local.AppDatabase
import com.mindset.data.local.RaceEventEntity
import com.mindset.data.local.SyncMeta
import com.mindset.data.local.SyncMetaKeys
import com.mindset.data.remote.RaceCalendarApi
import com.mindset.domain.repository.RaceCalendarRepository
import com.mindset.model.EventFormat
import com.mindset.model.RaceCity
import com.mindset.model.RaceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Race calendar backed by Room, filled from two places.
 *
 * **The floor** is [HyroxCalendarSeed], written on first read. Onboarding is the very first screen a
 * user ever sees — before any sync, often with no connectivity — so a network-only calendar would
 * mean an empty city picker on a plane. The seed guarantees the picker is never empty.
 *
 * **The overlay** is [refresh], which pulls the server's copy of the same catalog so the calendar can
 * be corrected between app releases. Remote rows overwrite the seed rows they share an id with
 * (the ids are derived from city + start date, so they line up), while seed rows the server has
 * dropped are left alone — a failed, empty, or truncated fetch can therefore never empty the picker.
 *
 * Reads observe the database only, per the architecture rule that the UI never reads the network.
 */
class RaceCalendarRepositoryImpl(
    private val database: AppDatabase,
    private val api: RaceCalendarApi,
    private val clock: Clock,
) : RaceCalendarRepository {
    private val dao get() = database.raceEventDao()
    private val syncMeta get() = database.syncMetaDao()

    /**
     * Seeding races two ways at once. The ViewModel fires [refresh] and subscribes to the picker in
     * separate coroutines, so without this the read path could write seed rows *after* a refresh had
     * already written the remote ones, silently reverting the corrections.
     */
    private val seedMutex = Mutex()

    override fun observeCities(): Flow<List<RaceCity>> = flow {
        ensureSeeded()
        emitAll(
            dao.observeCities(now(), EventFormat.HYROX)
                .map { rows -> rows.map { RaceCity(it.city, it.country) } },
        )
    }

    override fun observeEventsIn(city: RaceCity): Flow<List<RaceEvent>> = flow {
        ensureSeeded()
        emitAll(
            dao.observeByCity(city.city, city.country, now(), EventFormat.HYROX)
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override suspend fun refresh() {
        // Before anything remote lands, so the seed can never be written over the top of it afterwards.
        ensureSeeded()

        val response = api.calendar() ?: return
        if (response.events.isEmpty()) return

        // Nothing new to write — the catalog we already hold is this version or later.
        val known = syncMeta.get(SyncMetaKeys.RACE_CALENDAR_REMOTE_VERSION)?.toIntOrNull() ?: 0
        if (response.version in 1..known) return

        dao.upsertAll(response.events.map { it.toEntity(RaceEventEntity.SOURCE_REMOTE) })
        syncMeta.set(SyncMeta(SyncMetaKeys.RACE_CALENDAR_REMOTE_VERSION, response.version.toString()))
    }

    /**
     * Writes the bundled calendar once per [HyroxCalendarSeed.VERSION], matching the versioned-seed
     * idiom used for the exercise and event-format catalogs.
     *
     * A newer app ships a newer seed, which legitimately overwrites rows an older server correction
     * had patched — so the remote watermark is reset at the same time, letting the next [refresh]
     * re-apply the server's version instead of skipping it as already-seen.
     */
    private suspend fun ensureSeeded() = seedMutex.withLock {
        val seeded = syncMeta.get(SyncMetaKeys.RACE_CALENDAR_SEED_VERSION)?.toIntOrNull() ?: 0
        if (seeded >= HyroxCalendarSeed.VERSION) return@withLock

        dao.upsertAll(HyroxCalendarSeed.events.map { it.toEntity(RaceEventEntity.SOURCE_SEED) })
        syncMeta.set(SyncMeta(SyncMetaKeys.RACE_CALENDAR_SEED_VERSION, HyroxCalendarSeed.VERSION.toString()))
        syncMeta.set(SyncMeta(SyncMetaKeys.RACE_CALENDAR_REMOTE_VERSION, "0"))
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

private fun RaceEventDto.toEntity(source: String) = RaceEventEntity(
    id = id,
    city = city,
    country = country,
    startDate = startDate,
    endDate = endDate,
    formatKey = formatKey,
    source = source,
)

private fun RaceEventEntity.toDomain() = RaceEvent(
    id = id,
    city = city,
    country = country,
    startDate = Instant.fromEpochMilliseconds(startDate),
    endDate = Instant.fromEpochMilliseconds(endDate),
    formatKey = formatKey,
)
