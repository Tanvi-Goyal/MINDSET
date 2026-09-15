package com.mindset.server

import com.mindset.contracts.PullRequest
import com.mindset.contracts.PullResponse
import com.mindset.contracts.PushRequest
import com.mindset.contracts.PushResponse
import com.mindset.contracts.RaceCalendarResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::syncModule)
        .start(wait = true)
}

/** Wiring is a top-level extension so the `testApplication` harness can install the same module. */
fun Application.syncModule(
    store: SessionStore = InMemorySessionStore(),
    races: RaceStore = InMemoryRaceStore(),
) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        // Read-only reference data: the race calendar the onboarding picker reads. GET (not POST
        // like the sync routes) because it carries no body and is safe to cache. Clients ship the
        // same catalog as a bundled fallback, so an unreachable server degrades, never breaks.
        get("/reference/races") {
            val formatKey = call.request.queryParameters["format"]
            call.respond(
                RaceCalendarResponse(
                    events = races.events(formatKey),
                    version = races.version(),
                ),
            )
        }

        // Drain: apply each client change under LWW, report how many were accepted.
        post("/sync/push") {
            val request = call.receive<PushRequest>()
            var accepted = 0
            for (change in request.changes) {
                if (store.upsert(change)) accepted += 1
            }
            call.respond(PushResponse(accepted = accepted))
        }

        // Feed: everything changed since the client's cursor, plus the new high-water cursor.
        // Soft-deleted rows are included so deletions propagate to other devices.
        post("/sync/pull") {
            val request = call.receive<PullRequest>()
            val changes = store.changesSince(request.cursor)
            call.respond(PullResponse(changes = changes, nextCursor = store.currentSeq()))
        }
    }
}
