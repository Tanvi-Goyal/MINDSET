package com.mindset.data.remote

import com.mindset.contracts.RaceCalendarResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Transport for the race calendar reference endpoint. An interface for the same reason [SyncApi] is
 * one: it keeps the repository testable without an HTTP stack.
 */
interface RaceCalendarApi {
    /** The server's calendar, or null when it could not be fetched for any reason. */
    suspend fun calendar(): RaceCalendarResponse?
}

/**
 * Ktor-backed [RaceCalendarApi].
 *
 * Deliberately shaped like [WgerApi] and **not** like [KtorSyncApi]: it fails soft and returns null
 * rather than throwing. The calendar is refreshed opportunistically behind a screen the user is
 * already using, and the client ships a bundled copy of the same catalog — so an unreachable server,
 * a captive-portal redirect, or a malformed payload must degrade to "no update", never to an error
 * the user sees.
 *
 * A blank [baseUrl] (no host configured for this platform) short-circuits before the request.
 */
class KtorRaceCalendarApi(private val client: HttpClient, private val baseUrl: String) : RaceCalendarApi {
    override suspend fun calendar(): RaceCalendarResponse? {
        if (baseUrl.isBlank()) return null
        return runCatching {
            client.get("$baseUrl/reference/races?format=HYROX").body<RaceCalendarResponse>()
        }.getOrNull()
    }
}
