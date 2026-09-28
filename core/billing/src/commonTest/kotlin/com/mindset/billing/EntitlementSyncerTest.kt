package com.mindset.billing

import com.mindset.domain.Entitlement
import com.mindset.domain.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the syncer's write policy — what reaches the database, and what deliberately does not.
 * The RevenueCat half is stubbed out through the injected stream; these assertions are about the
 * rule that a store hiccup must never cost a paying user their subscription.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementSyncerTest {
    private class RecordingEntitlements : EntitlementRepository {
        val writes = mutableListOf<Boolean>()
        private val state = MutableStateFlow(Entitlement())

        override fun observe(): Flow<Entitlement> = state

        override suspend fun setPro(isPro: Boolean) {
            writes += isPro
            state.value = Entitlement(isPro = isPro)
        }
    }

    private fun syncer(repo: RecordingEntitlements, scope: CoroutineScope, stream: Flow<Boolean>) =
        EntitlementSyncer(entitlements = repo, scope = scope, proStream = { stream })

    @Test
    fun `an unavailable store writes nothing rather than demoting to free`() = runTest {
        val repo = RecordingEntitlements()

        // An offline seed fetch emits no value at all — the production stream swallows
        // PurchasesException without emitting.
        syncer(repo, backgroundScope, emptyFlow()).start()
        runCurrent()

        assertEquals(
            emptyList(),
            repo.writes,
            "A failed fetch means 'unknown', not 'not subscribed'. Writing false here would " +
                "revoke Pro from a paying user who opened the app without a network.",
        )
    }

    @Test
    fun `active entitlement is written through to the database`() = runTest {
        val repo = RecordingEntitlements()

        syncer(repo, backgroundScope, flowOf(true)).start()
        runCurrent()

        assertEquals(listOf(true), repo.writes)
    }

    @Test
    fun `repeated identical state does not re-write`() = runTest {
        val repo = RecordingEntitlements()

        // The store re-announces current state on every foreground and renewal check.
        syncer(repo, backgroundScope, flowOf(true, true, true)).start()
        runCurrent()

        assertEquals(listOf(true), repo.writes, "distinctUntilChanged should collapse these")
    }

    @Test
    fun `a lapsed subscription is written through`() = runTest {
        val repo = RecordingEntitlements()

        syncer(repo, backgroundScope, flowOf(true, false)).start()
        runCurrent()

        assertEquals(listOf(true, false), repo.writes)
    }

    @Test
    fun `start is idempotent`() = runTest {
        val repo = RecordingEntitlements()
        val subject = syncer(repo, backgroundScope, flowOf(true))

        subject.start()
        subject.start()
        runCurrent()

        assertEquals(listOf(true), repo.writes, "the second start must not open a second collector")
    }
}
