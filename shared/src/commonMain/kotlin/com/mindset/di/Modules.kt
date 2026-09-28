package com.mindset.di

import com.mindset.billing.di.billingModule
import com.mindset.common.di.commonModule
import com.mindset.data.di.dataModule
import com.mindset.data.local.di.databaseModule
import com.mindset.data.local.di.databasePlatformModule
import com.mindset.data.remote.di.networkModule
import com.mindset.data.remote.di.networkPlatformModule
import com.mindset.datastore.di.dataStorePlatformModule
import com.mindset.sync.di.syncModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Single entry point for starting Koin, called from each platform. It aggregates every module's Koin
 * module: the `:core:*` object graphs + platform seams (B7) and the per-feature ViewModel graphs
 * (B11). Each feature module owns its own `xModule` in package `com.mindset.di`, so they resolve here
 * with no import. [config] lets a platform add bindings it alone can supply — e.g. Android passes
 * `androidContext(this)`; iOS passes nothing.
 *
 * [observability] is applied AFTER [modules] — the Kotzilla profiler inspects the assembled graph,
 * so the ordering is load-bearing. Platforms pass `appObservability`, which is a no-op unless the
 * build opted into the profiler with `-Pmindset.profiler=true`.
 */
fun initKoin(
    config: KoinAppDeclaration? = null,
    observability: KoinAppDeclaration? = null,
): KoinApplication = startKoin {
    config?.invoke(this)
    modules(
        commonModule,
        databaseModule,
        databasePlatformModule,
        dataStorePlatformModule,
        networkModule,
        networkPlatformModule,
        dataModule,
        syncModule,
        billingModule,
        // Feature ViewModel graphs (each in package com.mindset.di in its feature module):
        homeModule,
        loggingModule,
        templatesModule,
        exercisesModule,
        historyModule,
        stationsModule,
        profileModule,
        onboardingModule,
    )
    observability?.invoke(this)
}
