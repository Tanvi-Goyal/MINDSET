package com.mindset.billing

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure

/**
 * Identifier of the entitlement that unlocks Pro, as configured in the RevenueCat dashboard. Read
 * only by [EntitlementSyncer]; every other module asks
 * [com.mindset.domain.repository.EntitlementRepository] instead, so a dashboard rename costs one
 * line here and nothing downstream.
 */
const val PRO_ENTITLEMENT_ID: String = "pro"

/**
 * Starts the RevenueCat SDK. Call once per process, before anything reads purchase state.
 *
 * [apiKey] is supplied by the platform rather than baked in here: Android reads it from
 * `BuildConfig` (debug builds get a Test Store key, release builds the Play key), and iOS would
 * pass its own. That keeps the key out of shared source and out of git.
 *
 * No Android `Context` is needed — the SDK captures the `Application` itself through an
 * `androidx.startup` initializer merged from its manifest, which is why this can live in
 * commonMain at all.
 */
fun configureBilling(apiKey: String, debugLogging: Boolean = false) {
    // A fresh clone has no local.properties and so no key. Leaving the SDK unconfigured degrades
    // to "billing unavailable" — offerings fail, the paywall reports it, and the rest of the app is
    // untouched because entitlement state is read from Room, not from here. Far better than
    // handing the SDK a blank key and failing somewhere less obvious.
    if (apiKey.isBlank()) return
    // Configuring twice throws; the guard makes this safe to call from a retry or a test harness.
    if (Purchases.isConfigured) return
    if (debugLogging) Purchases.logLevel = LogLevel.DEBUG
    Purchases.configure(apiKey = apiKey)
}
