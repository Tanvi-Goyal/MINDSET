package com.mindset

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.mindset.billing.EntitlementSyncer
import com.mindset.billing.configureBilling
import com.mindset.di.appObservability
import com.mindset.di.initKoin
import com.mindset.domain.ActiveWorkoutController
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

/**
 * Starts Koin once per process. The shared [initKoin] wires the common graph; here we hand it the
 * Android-only piece it can't get itself — the application [android.content.Context], which the
 * Room builder needs. This is where the platform half of the DI seam is supplied, and where the
 * debug-only Koin profiler is gated in.
 */
class MindSetApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            config = {
                androidLogger()
                androidContext(this@MindSetApplication)
            },
            // No-op unless the build was made with `-Pmindset.profiler=true`; see :shared's build
            // script for why the profiler is a compile-time and not a runtime switch.
            observability = appObservability,
        )
        // Billing. Configured BEFORE the syncer starts, because the syncer immediately reads
        // Purchases.sharedInstance. The key is Android-only (BuildConfig), which is exactly why
        // this lives here and not in shared code — debug builds carry a Test Store key, release
        // builds the Play key (see :app's build script).
        configureBilling(apiKey = BuildConfig.REVENUECAT_API_KEY, debugLogging = BuildConfig.DEBUG)
        // Drains store state into the `entitlement` table for the rest of the app to observe. Owns
        // its own scope, so this neither blocks startup nor needs a lifecycle to hang off.
        GlobalContext.get().get<EntitlementSyncer>().start()

        // Rehydrate a race that outlived the process. Fire-and-forget and idempotent — it does its
        // reads on the controller's own scope, so it never blocks startup, and a restored race is
        // published paused for the athlete to resume.
        GlobalContext.get().get<ActiveWorkoutController>().restore()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader
        .Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
