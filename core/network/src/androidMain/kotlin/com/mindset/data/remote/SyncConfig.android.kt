package com.mindset.data.remote

/**
 * The Android emulator reaches the host machine's localhost at the special alias `10.0.2.2` —
 * `127.0.0.1` there is the *emulator's own* loopback, where nothing is listening. On a physical
 * device neither works; that needs the dev machine's LAN address.
 *
 * Cleartext http is permitted in debug builds only (see `app/src/debug/AndroidManifest.xml`), so a
 * release build cannot reach this host. Every caller is required to degrade gracefully when the
 * server is unreachable — see `RaceCalendarApi`, which falls back to the bundled catalog.
 */
actual val syncBaseUrl: String = "http://10.0.2.2:8080"
