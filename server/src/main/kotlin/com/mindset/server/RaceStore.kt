package com.mindset.server

import com.mindset.contracts.HyroxCalendarSeed
import com.mindset.contracts.RaceEventDto

/**
 * Server-side source of the race calendar. Read-only and identical for every user, so unlike
 * [SessionStore] there is no sequence counter, no LWW and no mutex — nothing mutates.
 *
 * Interface so the in-memory impl can be swapped for a database-backed one (Phase 2) without
 * touching the route.
 */
interface RaceStore {
    /** Every known race, optionally narrowed to one event format (e.g. `"HYROX"`). */
    suspend fun events(formatKey: String? = null): List<RaceEventDto>

    /** Catalog version — clients skip re-writing their local copy when this hasn't moved. */
    suspend fun version(): Int
}

/**
 * Serves the calendar bundled in `:contracts`. Client and server therefore ship the *same* list:
 * a client that can't reach the server falls back to a byte-identical local copy, and updating the
 * calendar is one edit in [HyroxCalendarSeed] plus a redeploy.
 */
class InMemoryRaceStore(
    private val catalog: List<RaceEventDto> = HyroxCalendarSeed.events,
    private val version: Int = HyroxCalendarSeed.VERSION,
) : RaceStore {
    override suspend fun events(formatKey: String?): List<RaceEventDto> =
        if (formatKey == null) catalog else catalog.filter { it.formatKey.equals(formatKey, ignoreCase = true) }

    override suspend fun version(): Int = version
}
