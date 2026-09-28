package com.mindset.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindset.domain.repository.EntitlementRepository
import com.mindset.domain.repository.PreferencesRepository
import com.mindset.domain.repository.SessionRepository
import com.mindset.model.HyroxStation
import com.mindset.model.HyroxStationModel
import com.mindset.model.HyroxStationType
import com.mindset.model.PrKind
import com.mindset.model.StationAggregate
import com.mindset.model.StationRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class StationsViewModel(
    private val repository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {
    /**
     * Set by the persistent unlock affordance, so the athlete can reach the offer again after
     * dismissing the metered prompt. Separate from the metered trigger below, which is derived
     * from stored state and must not be re-armed by a tap.
     */
    private val manuallyOpened = MutableStateFlow(false)

    init {
        // Counted once per ViewModel rather than per tap. Bottom-nav back-stack entries keep this
        // VM alive, so in practice this counts app sessions that reached Stations — three separate
        // training-day visits, which is a better buying signal than three taps in one session.
        viewModelScope.launch { preferencesRepository.recordStationsOpened() }
    }

    private val board: Flow<StationsUiState> = preferencesRepository.observe().map { prefs ->
        RaceConfig(
            prefs.hyroxDivisionKey,
            prefs.gender?.name,
            prefs.raceMode?.name,
        )
    }.distinctUntilChanged().flatMapLatest { config ->
        val division = config.divisionKey
        if (division == null || config.gender == null || config.raceMode == null) {
            flowOf(StationsUiState(loading = false))
        } else {
            combine(
                repository.observeStationRecords(),
                repository.observeStationAggregates(division),
            ) { records, aggregates ->
                StationsUiState(
                    loading = false,
                    stations = buildBoard(
                        reference = repository.hyroxStations(
                            divisionKey = division,
                            raceMode = enumValueOf(config.raceMode),
                            gender = enumValueOf(config.gender),
                        ),
                        records = records.filter { it.divisionKey == division },
                        aggregates = aggregates,
                    ),
                )
            }
        }
    }

    val uiState: StateFlow<StationsUiState> = combine(
        board,
        entitlements.observe(),
        preferencesRepository.observe()
            .map { PaywallGate(it.stationsViewCount, it.hasSeenStationsPaywall) }
            .distinctUntilChanged(),
        manuallyOpened,
    ) { board, entitlement, gate, manual ->
        board.copy(
            isPro = entitlement.isPro,
            // The tab itself always opens; this only decides whether the sheet rides on top of it.
            showPaywall = !entitlement.isPro &&
                (manual || (!gate.seen && gate.views >= VIEWS_BEFORE_PAYWALL)),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StationsUiState(),
    )

    /** Opens the paywall on demand, from the persistent unlock affordance. */
    fun openPaywall() {
        manuallyOpened.value = true
    }

    /**
     * Closes the paywall and records that it has been seen, so the metered prompt fires once and
     * then never nags. The offer stays reachable through [openPaywall].
     */
    fun dismissPaywall() {
        manuallyOpened.value = false
        viewModelScope.launch { preferencesRepository.setStationsPaywallSeen() }
    }
}

/** Opens the Stations tab before the paywall is offered. Third visit prompts. */
private const val VIEWS_BEFORE_PAYWALL = 3

private data class PaywallGate(val views: Int, val seen: Boolean)

private data class RaceConfig(val divisionKey: String?, val gender: String?, val raceMode: String?)

internal const val DASH = "—"

private fun buildBoard(
    reference: List<HyroxStationModel>,
    records: List<StationRecord>,
    aggregates: Map<HyroxStation, StationAggregate>,
): List<StationCardUi> = reference.filter {
    it.stationType == HyroxStationType.STATION && it.station != null
}.map { segment ->
    val station = requireNotNull(segment.station)
    val aggregate = aggregates[station]
    StationCardUi(
        station = station,
        name = displayName(segment.title),
        targetLabel = targetLabel(segment),
        recentLabel = aggregate?.recentTimeSec?.let(::formatTime) ?: DASH,
        pbLabel = bestTime(records, station, bucketFor(segment))?.let(::formatTime) ?: DASH,
        sessionsLabel = (aggregate?.sessionCount ?: 0).toString(),
        trend = trendOf(aggregate),
    )
}.sortedBy { it.station.ordinal }

private fun bestTime(records: List<StationRecord>, station: HyroxStation, bucket: Int?): Int? = records.filter {
    it.station == station &&
        it.kind == PrKind.BEST_TIME &&
        it.bucket == bucket
}.minOfOrNull { it.value }?.toInt()

private fun bucketFor(segment: HyroxStationModel): Int? = segment.targetReps ?: segment.targetDistanceM

private fun targetLabel(segment: HyroxStationModel): String = listOfNotNull(
    when {
        segment.targetReps != null -> "${segment.targetReps} REPS"
        segment.targetDistanceM != null -> "${segment.targetDistanceM}M"
        else -> null
    },
    segment.loadDisplay?.takeIf { it.isNotBlank() }?.uppercase(),
).joinToString(TARGET_SEPARATOR)

private const val TARGET_SEPARATOR = " · "

private fun trendOf(aggregate: StationAggregate?): StationTrendUi? {
    val recent = aggregate?.recentTimeSec ?: return null
    val previous = aggregate.previousTimeSec ?: return null

    val deltaSec = recent - previous
    if (deltaSec == 0) return null

    val sign = if (deltaSec < 0) "-" else "+"
    return StationTrendUi(label = "$sign${abs(deltaSec)}s", improving = deltaSec < 0)
}

private fun displayName(title: String): String = title.substringAfter(". ", missingDelimiterValue = title)

/** Whole seconds → `MM:SS` (135 → "02:15"), matching the board's fixed-width metric columns. */
private fun formatTime(totalSec: Int): String = "${(totalSec / 60).toString().padStart(2, '0')}:${(totalSec % 60).toString().padStart(2, '0')}"
