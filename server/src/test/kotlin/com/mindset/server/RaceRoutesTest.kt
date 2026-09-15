package com.mindset.server

import com.mindset.contracts.HyroxCalendarSeed
import com.mindset.contracts.RaceCalendarResponse
import com.mindset.contracts.RaceEventDto
import com.mindset.contracts.isoDateToUtcMillis
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RaceRoutesTest {
    @Test
    fun reference_races_serves_the_bundled_catalog() = testApplication {
        application { syncModule() }
        val response: RaceCalendarResponse = createJsonClient().get("/reference/races").body()

        assertEquals(HyroxCalendarSeed.events.size, response.events.size)
        assertEquals(HyroxCalendarSeed.VERSION, response.version)
    }

    @Test
    fun format_query_narrows_the_catalog() = testApplication {
        application {
            syncModule(
                races = InMemoryRaceStore(
                    catalog = listOf(
                        RaceEventDto(id = "h", city = "Rome", formatKey = "HYROX"),
                        RaceEventDto(id = "d", city = "Berlin", formatKey = "DEKA"),
                    ),
                ),
            )
        }
        val client = createJsonClient()

        val hyrox: RaceCalendarResponse = client.get("/reference/races?format=HYROX").body()
        assertEquals(listOf("Rome"), hyrox.events.map { it.city })

        val all: RaceCalendarResponse = client.get("/reference/races").body()
        assertEquals(2, all.events.size, "no format filter should serve everything")
    }

    @Test
    fun every_seeded_race_has_an_id_a_city_and_a_sane_date_window() {
        for (event in HyroxCalendarSeed.events) {
            assertTrue(event.id.isNotBlank(), "blank id for ${event.city}")
            assertTrue(event.city.isNotBlank(), "blank city for ${event.id}")
            assertTrue(event.country.isNotBlank(), "blank country for ${event.id}")
            assertTrue(
                event.endDate >= event.startDate,
                "${event.id} ends (${event.endDate}) before it starts (${event.startDate})",
            )
        }
        assertEquals(
            HyroxCalendarSeed.events.size,
            HyroxCalendarSeed.events.distinctBy { it.id }.size,
            "race ids must be unique — they are the upsert key on the client",
        )
    }

    @Test
    fun iso_dates_convert_to_utc_midnight() {
        assertEquals(0L, isoDateToUtcMillis("1970-01-01"))
        assertEquals(951_782_400_000L, isoDateToUtcMillis("2000-02-29")) // leap day
        assertEquals(1_789_171_200_000L, isoDateToUtcMillis("2026-09-12"))
        assertEquals(-86_400_000L, isoDateToUtcMillis("1969-12-31"))
    }
}

private fun io.ktor.server.testing.ApplicationTestBuilder.createJsonClient() =
    createClient { install(ContentNegotiation) { json() } }
