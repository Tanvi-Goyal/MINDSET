package com.mindset.contracts

import kotlinx.serialization.Serializable

/**
 * One race weekend on the HYROX calendar.
 *
 * A race is a *date*, not an instant, but the whole app already carries race timestamps as epoch
 * millis ([com.mindset.contracts.SessionDto] style, and M3's `DatePicker` hands back UTC midnight),
 * so dates travel as **epoch millis at UTC midnight** to stay consistent with `RaceGoal.targetDate`.
 *
 * [endDate] is inclusive and equals [startDate] for a single-day event. Most HYROX events run a
 * multi-day window with heats spread across it, so the athlete picks their own day inside the range.
 */
@Serializable
data class RaceEventDto(
    val id: String = "",
    val city: String = "",
    val country: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val formatKey: String = "HYROX",
)

/** Payload of `GET /reference/races`. [version] lets a client skip re-writing an unchanged catalog. */
@Serializable
data class RaceCalendarResponse(
    val events: List<RaceEventDto> = emptyList(),
    val version: Int = 0,
)
