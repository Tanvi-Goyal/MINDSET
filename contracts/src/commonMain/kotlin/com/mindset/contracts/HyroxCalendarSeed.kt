package com.mindset.contracts

/**
 * The HYROX race calendar, shared verbatim by the server (which serves it from
 * `GET /reference/races`) and by the client (which seeds it into Room so onboarding works offline
 * and on first launch, before any network call has ever succeeded). Keeping the single copy here in
 * `:contracts` is what stops the two sides drifting.
 *
 * **This is data, not code** — treat it like the Hyrox station standards. Dates must be checked
 * against the official calendar at <https://hyrox.com/find-my-race/> before release; hyrox.com
 * publishes no API, so this list is transcribed by hand and goes stale on its own. Bump [VERSION]
 * whenever the list changes so existing installs re-seed.
 */
object HyroxCalendarSeed {
    const val VERSION = 1

    val events: List<RaceEventDto> = listOf(
        race("Beijing", "China", "2026-09-12", "2026-09-13"),
        race("Maastricht", "Netherlands", "2026-09-17", "2026-09-20"),
        race("Salt Lake City", "United States", "2026-09-18", "2026-09-20"),
        race("Rome", "Italy", "2026-09-23", "2026-09-27"),
        race("Oslo", "Norway", "2026-09-25", "2026-09-27"),
        race("Bordeaux", "France", "2026-09-30", "2026-10-04"),
        race("Toronto", "Canada", "2026-10-01", "2026-10-04"),
        race("Karlsruhe", "Germany", "2026-10-01", "2026-10-04"),
        race("Boston", "United States", "2026-10-08", "2026-10-11"),
        race("Geneva", "Switzerland", "2026-10-09", "2026-10-11"),
        race("Gdańsk", "Poland", "2026-10-10", "2026-10-11"),
        race("Valencia", "Spain", "2026-10-15", "2026-10-18"),
        race("São Paulo", "Brazil", "2026-10-17", "2026-10-17"),
        race("Tampa", "United States", "2026-10-23", "2026-10-25"),
        race("Birmingham", "United Kingdom", "2026-10-27", "2026-11-01"),
        race("Nice", "France", "2026-10-29", "2026-11-01"),
        race("Shanghai", "China", "2026-10-31", "2026-11-01"),
        race("Düsseldorf", "Germany", "2026-11-11", "2026-11-15"),
        race("Barcelona", "Spain", "2026-11-11", "2026-11-15"),
        race("Denver", "United States", "2026-11-12", "2026-11-15"),
        race("Seoul", "South Korea", "2026-11-14", "2026-11-15"),
        race("Dallas", "United States", "2026-11-18", "2026-11-22"),
        race("Poznań", "Poland", "2026-11-20", "2026-11-22"),
        race("Guangzhou", "China", "2026-11-21", "2026-11-22"),
        race("Utrecht", "Netherlands", "2026-11-26", "2026-11-30"),
        race("London", "United Kingdom", "2026-12-02", "2026-12-06"),
        race("Anaheim", "United States", "2026-12-03", "2026-12-06"),
        race("Milan", "Italy", "2026-12-05", "2026-12-06"),
        race("Frankfurt", "Germany", "2026-12-10", "2026-12-13"),
        race("Nashville", "United States", "2026-12-10", "2026-12-13"),
        race("Paris", "France", "2026-12-12", "2026-12-20"),
        race("Gent", "Belgium", "2026-12-17", "2026-12-20"),
        race("Helsinki", "Finland", "2026-12-18", "2026-12-20"),
        race("Vancouver", "Canada", "2026-12-18", "2026-12-20"),
    )
}

/** Builds one event, deriving a stable id from city + start date so re-seeding is idempotent. */
private fun race(city: String, country: String, start: String, end: String): RaceEventDto =
    RaceEventDto(
        id = "hyrox-${city.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-$start",
        city = city,
        country = country,
        startDate = isoDateToUtcMillis(start),
        endDate = isoDateToUtcMillis(end),
    )

/**
 * `yyyy-MM-dd` → epoch millis at UTC midnight, without pulling in kotlinx-datetime for four lines of
 * arithmetic. Uses Howard Hinnant's days-from-civil algorithm, which is exact for all proleptic
 * Gregorian dates.
 */
fun isoDateToUtcMillis(iso: String): Long {
    val (y, m, d) = iso.split("-").map { it.toInt() }
    val year = if (m <= 2) y - 1 else y
    val era = (if (year >= 0) year else year - 399) / 400
    val yearOfEra = year - era * 400
    val dayOfYear = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    val days = era * 146_097L + dayOfEra - 719_468L
    return days * 86_400_000L
}
