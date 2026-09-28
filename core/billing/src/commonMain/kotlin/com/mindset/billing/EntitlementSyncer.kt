package com.mindset.billing

import com.mindset.domain.repository.EntitlementRepository
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Writes RevenueCat's purchase state into the local `entitlement` table, and nothing else.
 *
 * This is the app's **only** bridge between the store and the database. It exists so the rest of
 * the app keeps obeying the offline-first rule: the UI observes [EntitlementRepository] (a Flow off
 * Room) and never learns that a network SDK is involved. Pro therefore survives airplane mode,
 * process death and a cold start — the store is a *writer* of entitlement state, never the thing a
 * screen reads.
 *
 * Start it once, at app startup, after [configureBilling].
 *
 * [proStream] is injectable so the write policy can be tested without the SDK's global singleton;
 * production uses the default [revenueCatProStream].
 */
class EntitlementSyncer(
    private val entitlements: EntitlementRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val proStream: () -> Flow<Boolean> = ::revenueCatProStream,
) {
    private var job: Job? = null

    /** Begins syncing. Idempotent — a second call while already running is ignored. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            proStream()
                // The store re-announces unchanged state on every foreground and every renewal
                // check. Without this, each one is a redundant Room write that re-emits through
                // every observing Flow and recomposes the gated screens for no reason.
                .distinctUntilChanged()
                .collect { isPro -> entitlements.setPro(isPro) }
        }
    }

    /** Stops syncing and releases the SDK delegate. The app never calls this; tests do. */
    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Bridges RevenueCat's callback delegate into a Flow of "is the Pro entitlement active".
 *
 * `Purchases.delegate` is a single app-wide slot: assigning it replaces whatever was there, and
 * [awaitClose] nulls it out again. Two simultaneous collectors would therefore fight over that slot
 * — the second would displace the first, and the first's cancellation would then tear down the
 * second's listener. [EntitlementSyncer] collects this exactly once, which is what makes that race
 * impossible; do not collect it from anywhere else.
 */
private fun revenueCatProStream(): Flow<Boolean> = callbackFlow {
    // Install the listener BEFORE the seed fetch: a purchase completing mid-fetch would otherwise
    // land in the gap between the two and be lost until the next app launch.
    Purchases.sharedInstance.delegate = object : PurchasesDelegate {
        override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
            trySend(customerInfo)
        }

        // Store-promoted purchases (an App Store product page) are not a surface we support.
        // Never calling `startPurchase` is the documented way to decline one.
        override fun onPurchasePromoProduct(
            product: StoreProduct,
            startPurchase: (
                onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
                onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit,
            ) -> Unit,
        ) = Unit
    }

    // Seed from cache-or-network so a returning user's state is right before the first delegate
    // callback ever fires.
    try {
        trySend(Purchases.sharedInstance.awaitCustomerInfo())
    } catch (_: PurchasesException) {
        // Deliberately emits NOTHING rather than `false`. A failed fetch means "unknown", not "not
        // subscribed" — emitting false here would demote a paying user who opened the app on a
        // plane. Room keeps the last known value and the delegate corrects it once the network is
        // back. See EntitlementSyncerTest.
        //
        // Caught narrowly on purpose: `catch (e: Exception)` would also swallow
        // CancellationException and break structured concurrency on teardown.
    }

    awaitClose { Purchases.sharedInstance.delegate = null }
}
    .map { it.entitlements[PRO_ENTITLEMENT_ID]?.isActive == true }
    // Only the newest state matters; a superseded one is never worth delivering late.
    .conflate()
