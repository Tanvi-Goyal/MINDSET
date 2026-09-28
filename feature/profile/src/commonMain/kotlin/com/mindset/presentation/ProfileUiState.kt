package com.mindset.presentation

import androidx.compose.runtime.Immutable

/**
 * Everything the Profile screen renders, already derived — the composables read primitives and
 * read-only lists only, never domain types. Flat rather than Home's widget slots: Profile's layout
 * is fixed and every field comes from the same local DB, so there is no per-card ordering to persist
 * and no per-card failure worth isolating.
 *
 * `@Immutable` is sound because each emission is rebuilt fresh and never mutated in place; it is
 * what lets Compose value-skip the cards whose data did not change.
 */
@Immutable
data class ProfileUiState(
    /** True until the first DB emission, so the header never flashes an empty name and "0 sessions". */
    val isLoading: Boolean = true,
    val athleteName: String = "",
    /** Division + race mode, e.g. "MEN PRO · SINGLES". Empty when onboarding never set them. */
    val tierLabel: String = "",
    val streakDays: Int = 0,
    val totalSessions: Int = 0,
    /** Oldest → newest; the last entry is the in-progress week. Empty until loaded. */
    val frequency: List<WeekFrequencyUi> = emptyList(),
    /**
     * Subscription entitlement, read from the local DB (never the store) like every other field
     * here. Decides whether the subscription card offers Customer Center or the paywall.
     */
    val isPro: Boolean = false,
)

/** One bar of the training-frequency chart: its axis [label], its height driver, and whether it is "now". */
@Immutable
data class WeekFrequencyUi(
    val label: String,
    val sessionCount: Int,
    val isCurrent: Boolean,
)
