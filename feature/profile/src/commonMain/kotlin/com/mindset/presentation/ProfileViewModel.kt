@file:OptIn(ExperimentalTime::class)

package com.mindset.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindset.domain.UserPreferences
import com.mindset.domain.repository.AthleteProfileRepository
import com.mindset.domain.repository.EntitlementRepository
import com.mindset.domain.repository.PreferencesRepository
import com.mindset.domain.repository.SessionRepository
import com.mindset.domain.trainingStreakDays
import com.mindset.domain.weeklySessionCounts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private const val DAY_MS = 86_400_000L

private const val FREQUENCY_WEEKS = 8

class ProfileViewModel(
    private val sessionRepository: SessionRepository,
    athleteProfileRepository: AthleteProfileRepository,
    preferencesRepository: PreferencesRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        athleteProfileRepository.observe(),
        preferencesRepository.observe(),
        sessionDerived(),
        entitlements.observe(),
    ) { profile, prefs, derived, entitlement ->
        ProfileUiState(
            isLoading = false,
            athleteName = profile.fullName.ifBlank { "Athlete" },
            tierLabel = tierLabel(prefs),
            streakDays = derived.streakDays,
            totalSessions = derived.totalSessions,
            frequency = derived.frequency,
            isPro = entitlement.isPro,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    /**
     * Every session-derived value from ONE read of the session table. Folding the streak, the count
     * and the bars into a single arm matters: collecting [SessionRepository.observeSessions] twice
     * would issue two independent Room queries for identical rows.
     *
     * `distinctUntilChanged` on the result is what lets the chart card skip recomposition — Compose
     * 2.4 has strong skipping on by default, so the unstable `List<WeekFrequencyUi>` param is
     * compared by instance. Dropping an equal emission here means `combine` re-offers the *same*
     * list instance, and the card skips instead of re-laying-out eight bars for a session-note edit.
     */
    private fun sessionDerived(): Flow<SessionDerived> =
        combine(sessionRepository.observeSessions(), todayEpochDay()) { sessions, today ->
            val startMillis = sessions.map { it.startedAt.toEpochMilliseconds() }
            val nowMillis = today * DAY_MS
            SessionDerived(
                streakDays = trainingStreakDays(startMillis, nowMillis),
                totalSessions = sessions.size,
                frequency = weeklySessionCounts(startMillis, nowMillis, FREQUENCY_WEEKS)
                    .mapIndexed { index, bucket ->
                        WeekFrequencyUi(
                            label = "W${index + 1}",
                            sessionCount = bucket.sessionCount,
                            isCurrent = bucket.isCurrent,
                        )
                    },
            )
        }.distinctUntilChanged()

    /**
     * The current UTC epoch-day, re-emitted just after each UTC midnight.
     *
     * `HomeViewModel.performanceLikeSlot` reads `Clock.System.now()` once, when the flow is *built*,
     * so a screen left open overnight keeps bucketing against yesterday. Pushing the day in as a
     * flow re-derives the window on rollover instead. Sleeping to the next boundary rather than
     * polling costs ~0 wakeups per screen session, and `WhileSubscribed(5_000)` cancels it 5s after
     * the screen backgrounds.
     */
    private fun todayEpochDay(): Flow<Long> = flow {
        while (true) {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            emit(nowMillis / DAY_MS)
            delay((DAY_MS - (nowMillis % DAY_MS) + 1_000L).milliseconds)
        }
    }.distinctUntilChanged()

    private fun tierLabel(prefs: UserPreferences): String =
        listOfNotNull(prefs.hyroxDivisionKey?.replace('_', ' '), prefs.raceMode?.name)
            .joinToString(" · ") { it.uppercase() }

    private data class SessionDerived(
        val streakDays: Int,
        val totalSessions: Int,
        val frequency: List<WeekFrequencyUi>,
    )
}
