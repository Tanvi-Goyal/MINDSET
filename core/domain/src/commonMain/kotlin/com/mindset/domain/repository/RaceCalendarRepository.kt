package com.mindset.domain.repository

import com.mindset.model.RaceCity
import com.mindset.model.RaceEvent
import kotlinx.coroutines.flow.Flow

/**
 * The race calendar the onboarding picker reads.
 *
 * Offline-first like everything else: both methods observe the local database, never the network.
 * [refresh] is the only thing that touches the network, and it feeds the database — callers fire it
 * and forget, because a bundled copy of the calendar is always present.
 */
interface RaceCalendarRepository {
    /** Cities with at least one race that hasn't finished yet, nearest race first. */
    fun observeCities(): Flow<List<RaceCity>>

    /** Upcoming races in [city], soonest first. */
    fun observeEventsIn(city: RaceCity): Flow<List<RaceEvent>>

    /**
     * Pulls the latest calendar into the database. Never throws and never reports failure: the
     * caller has nothing useful to do about it, and the bundled calendar already backs the UI.
     */
    suspend fun refresh()
}
