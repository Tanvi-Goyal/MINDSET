package com.mindset.data.remote.di

import com.mindset.data.remote.KtorRaceCalendarApi
import com.mindset.data.remote.KtorSyncApi
import com.mindset.data.remote.RaceCalendarApi
import com.mindset.data.remote.SyncApi
import com.mindset.data.remote.WgerApi
import com.mindset.data.remote.createHttpClient
import com.mindset.data.remote.syncBaseUrl
import org.koin.core.module.Module
import org.koin.dsl.module

/** Network graph: HTTP client (from the platform engine) → sync transport + reference clients. */
val networkModule =
    module {
        single { createHttpClient(get()) }
        single<SyncApi> { KtorSyncApi(get(), syncBaseUrl) }
        single { WgerApi(get()) }
        single<RaceCalendarApi> { KtorRaceCalendarApi(get(), syncBaseUrl) }
    }

/**
 * Platform-supplied HTTP engine: OkHttp on Android, Darwin on iOS. The client config is common
 * (see [createHttpClient]); only the engine is platform-specific, so the seam is one type wide.
 */
expect val networkPlatformModule: Module
