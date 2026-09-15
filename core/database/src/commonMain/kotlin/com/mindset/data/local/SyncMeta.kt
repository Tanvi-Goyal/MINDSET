package com.mindset.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A tiny key/value table for sync bookkeeping — currently just the pull cursor. Reuses Room
 * rather than pulling in DataStore for a single value. Keyed by a string so more sync state can
 * be added later without a schema change.
 */
@Entity(tableName = "sync_meta")
data class SyncMeta(@PrimaryKey val key: String, val value: String)

/** Well-known keys for [SyncMeta]. */
object SyncMetaKeys {
    const val PULL_CURSOR = "pull_cursor"

    /** Version of the seeded reference catalog on this device; bump to re-import (see repository). */
    const val SEED_VERSION = "seed_version"

    /** Version of the seeded program templates on this device; bump to re-seed (see repository). */
    const val TEMPLATE_SEED_VERSION = "template_seed_version"

    /** Version of the seeded event-format reference tables on this device; bump to re-seed (see repository). */
    const val EVENT_SEED_VERSION = "event_seed_version"

    /** Version of the bundled race calendar written to this device; bump to re-seed (see repository). */
    const val RACE_CALENDAR_SEED_VERSION = "race_calendar_seed_version"

    /** Catalog version last accepted from the reference endpoint; skips redundant local writes. */
    const val RACE_CALENDAR_REMOTE_VERSION = "race_calendar_remote_version"
}
