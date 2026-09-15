@file:OptIn(ExperimentalTime::class)

package com.mindset.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One race weekend on the event calendar — the thing an athlete picks when setting a [RaceGoal].
 *
 * Reference data: identical on every device, never synced, and it carries no envelope. [endDate] is
 * inclusive and equals [startDate] for a single-day event; most HYROX events run a multi-day window
 * with heats spread across it, so [raceDays] enumerates the days an athlete could actually be racing.
 */
data class RaceEvent(
    val id: String,
    val city: String,
    val country: String,
    val startDate: Instant,
    val endDate: Instant,
    val formatKey: String = EventFormat.HYROX,
) {
    /** `"Rome, Italy"` — how the picker labels this event. */
    val location: String get() = "$city, $country"

    /** Whether the window spans more than one day, i.e. the athlete must choose a day. */
    val isMultiDay: Boolean get() = endDate > startDate

    /**
     * Every day in the inclusive window, as UTC midnights. Single-day events yield exactly one entry.
     * Bounded by the window itself, which is days, not months — no risk of an unbounded list.
     */
    val raceDays: List<Instant>
        get() {
            val start = startDate.toEpochMilliseconds()
            val end = endDate.toEpochMilliseconds()
            return generateSequence(start) { it + MILLIS_PER_DAY }
                .takeWhile { it <= end }
                .map(Instant::fromEpochMilliseconds)
                .toList()
        }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/**
 * A place on the calendar, as the picker lists it. Country is part of the identity, not decoration:
 * "Birmingham" is both a UK and a US race city, so a city name alone cannot select an event.
 */
data class RaceCity(val city: String, val country: String) {
    val label: String get() = "$city, $country"
}
