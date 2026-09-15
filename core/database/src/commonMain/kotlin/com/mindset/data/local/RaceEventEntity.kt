package com.mindset.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One race weekend on the event calendar — reference data the onboarding picker reads.
 *
 * Like [EventFormatEntity] this carries **no sync envelope and no outbox row**: it is identical on
 * every device and only ever flows server → client. Unlike the other reference tables it cannot be
 * a pure seed, because the calendar changes between app releases — so rows arrive from two places
 * and [source] records which, letting a remote row take precedence over the bundled one it replaces
 * while seed rows the server doesn't know about survive.
 *
 * Dates are epoch millis at UTC midnight. [endDate] is inclusive and equals [startDate] for a
 * single-day event.
 */
@Entity(tableName = "race_event", indices = [Index("city"), Index("startDate")])
data class RaceEventEntity(
    @PrimaryKey val id: String,
    val city: String,
    val country: String,
    val startDate: Long,
    val endDate: Long,
    val formatKey: String,
    val source: String,
) {
    companion object {
        /** Shipped inside the app binary — always present, may be stale. */
        const val SOURCE_SEED = "SEED"

        /** Fetched from the reference endpoint — wins over a [SOURCE_SEED] row with the same id. */
        const val SOURCE_REMOTE = "REMOTE"
    }
}
