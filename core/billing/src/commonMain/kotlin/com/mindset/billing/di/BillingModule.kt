package com.mindset.billing.di

import com.mindset.billing.EntitlementSyncer
import org.koin.dsl.module

/**
 * Billing graph. [EntitlementSyncer] is a `single` because it owns the SDK's one delegate slot —
 * a second instance would silently displace the first's listener. Plain constructor wiring, as
 * elsewhere in the app.
 */
val billingModule =
    module {
        single { EntitlementSyncer(get()) }
    }
