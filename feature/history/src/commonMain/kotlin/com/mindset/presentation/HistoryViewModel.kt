@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.mindset.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mindset.domain.longestStreakDays
import com.mindset.domain.personalBestSessionIds
import com.mindset.domain.repository.EntitlementRepository
import com.mindset.domain.repository.SessionRepository
import com.mindset.domain.trainingStreakDays
import com.mindset.model.Session
import com.mindset.model.SessionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A history row: the session plus its computed volume. */
data class HistoryRow(val session: Session, val volumeKg: Double, val durationSec: Int?)

/** The workout-type filter behind the History header's filter control. */
enum class HistoryFilter { ALL, STRENGTH, CONDITIONING, HYROX, MIXED }

private fun HistoryFilter.toSessionType(): SessionType? = if (this == HistoryFilter.ALL) null else SessionType.valueOf(name)

/** Immutable UI state for the History tab (free + pro variants gate on [isPro]). */
data class HistoryUiState(
    /** Entitlement gate — free (paywall) vs pro (full paged history). */
    val isPro: Boolean = false,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val streakDays: Int = 0,
    val longestStreakDays: Int = 0,
    /** UTC epoch-days that had ≥1 session — drives the streak-calendar highlighting. */
    val trainedEpochDays: Set<Long> = emptySet(),
    val todayEpochDay: Long = 0,
    /** Whole-history volume + PB lookups, keyed by session id — used to render both tiers' rows. */
    val volumes: Map<String, Double> = emptyMap(),
    /** Training time per session id (Σ logged splits, runs included) — the Pro list's rows. */
    val durations: Map<String, Int> = emptyMap(),
    val pbSessionIds: Set<String> = emptySet(),
    /** Free tier only: last-30-days sessions (non-paged). */
    val freeRows: List<HistoryRow> = emptyList(),
)

private const val DAY_MS = 86_400_000L
private const val THIRTY_DAYS_MS = 30 * DAY_MS

/**
 * Backs the History tab — reactive from the DB (never the network). The whole-history read powers the
 * streak calendar, PB detection, volumes and the free-tier 30-day list; the Pro list is a separate
 * Paging 3 stream ([pagedSessions]) so the visible list stays cheap for large histories.
 * CI timing probe: this comment exists only to force a realistic recompile. Not for merge.
 */
class HistoryViewModel(private val repository: SessionRepository, entitlements: EntitlementRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> =
        combine(
            repository.observeSessions(),
            repository.observeVolumesBySession(),
            repository.observeDurationsBySession(),
            entitlements.observe(),
            filter,
        ) { sessions, volumes, durations, entitlement, activeFilter ->
            val now = Clock.System.now().toEpochMilliseconds()
            val trainedEpochDays =
                sessions
                    .map { it.startedAt.toEpochMilliseconds() / DAY_MS }
                    .toSet()
            HistoryUiState(
                isPro = entitlement.isPro,
                filter = activeFilter,
                streakDays =
                trainingStreakDays(
                    sessions.map { it.startedAt.toEpochMilliseconds() },
                    now,
                ),
                longestStreakDays = longestStreakDays(trainedEpochDays),
                trainedEpochDays = trainedEpochDays,
                todayEpochDay = now / DAY_MS,
                volumes = volumes,
                durations = durations,
                pbSessionIds = personalBestSessionIds(sessions, volumes),
                freeRows =
                sessions
                    .filter { it.startedAt.toEpochMilliseconds() >= now - THIRTY_DAYS_MS }
                    .map { HistoryRow(it, volumes[it.id] ?: 0.0, durations[it.id]) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    /** The Pro reverse-chronological list, paged and re-fetched whenever the filter changes. */
    val pagedSessions: Flow<PagingData<Session>> =
        filter
            .flatMapLatest { active -> repository.pagedSessions(active.toSessionType()) }
            .cachedIn(viewModelScope)

    /** Intent: narrow the feed to a workout type (or [HistoryFilter.ALL] to clear). */
    fun onFilterSelected(newFilter: HistoryFilter) {
        filter.value = newFilter
    }
}
