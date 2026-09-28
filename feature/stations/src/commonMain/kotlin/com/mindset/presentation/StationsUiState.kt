package com.mindset.presentation

import androidx.compose.runtime.Immutable
import com.mindset.model.HyroxStation

@Immutable
data class StationsUiState(
    val loading: Boolean = true,
    val stations: List<StationCardUi> = emptyList(),
    /** Entitlement gate. Pro hides the upsell affordance; the board itself is never gated. */
    val isPro: Boolean = false,
    /** Whether the paywall sheet is riding on top of the board. */
    val showPaywall: Boolean = false,
)

@Immutable
data class StationCardUi(
    val station: HyroxStation,
    val name: String,
    val targetLabel: String,
    val recentLabel: String,
    val pbLabel: String,
    val sessionsLabel: String,
    val trend: StationTrendUi? = null,
)

@Immutable
data class StationTrendUi(val label: String, val improving: Boolean)
