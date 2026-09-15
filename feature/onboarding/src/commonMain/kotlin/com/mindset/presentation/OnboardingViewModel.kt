@file:OptIn(ExperimentalTime::class)

package com.mindset.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindset.domain.AthleteProfile
import com.mindset.domain.HeightUnit
import com.mindset.domain.Units
import com.mindset.domain.repository.AthleteProfileRepository
import com.mindset.domain.repository.PreferencesRepository
import com.mindset.domain.repository.RaceCalendarRepository
import com.mindset.domain.repository.RaceGoalRepository
import com.mindset.model.EventFormat
import com.mindset.model.Gender
import com.mindset.model.RaceCity
import com.mindset.model.RaceMode
import com.mindset.model.Tier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class OnboardingStep { ATHLETE_PROFILE, RACE_CONFIG }

class OnboardingViewModel(
    private val repository: AthleteProfileRepository,
    private val raceGoals: RaceGoalRepository,
    private val preferences: PreferencesRepository,
    private val raceCalendar: RaceCalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState(todayUtcMillis = todayUtcMillis(clock)))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Collector for the selected city's races; cancelled and replaced whenever the city changes. */
    private var cityEventsJob: Job? = null

    init {
        // Restore the remembered entry unit so backing out and returning keeps the user's choice.
        viewModelScope.launch {
            val remembered = preferences.observe().first().heightUnit
            _state.update { it.copy(heightUnit = remembered) }
        }
        // The picker reads the database, which always holds at least the bundled calendar…
        viewModelScope.launch {
            raceCalendar.observeCities().collect { cities -> _state.update { it.copy(cities = cities) } }
        }
        // …and this tops it up from the server if it happens to be reachable. Fire-and-forget by
        // design: refresh() never throws, and the UI is already backed by local data either way.
        viewModelScope.launch { raceCalendar.refresh() }
    }

    fun onFullName(value: String) = _state.update { it.copy(fullName = value) }

    fun onBodyweight(value: String) = _state.update { it.copy(bodyweightKg = value.sanitizeDecimal()) }

    fun onHeightFt(value: String) =
        _state.update { it.copy(heightFt = value.sanitizeInt(max = HeightBounds.MAX_FT)) }

    fun onHeightInches(value: String) =
        _state.update { it.copy(heightInches = value.sanitizeInt(max = HeightBounds.MAX_INCHES)) }

    fun onHeightCm(value: String) =
        _state.update { it.copy(heightCm = value.sanitizeInt(max = HeightBounds.MAX_CM)) }

    /**
     * Switches the height entry unit, carrying the current value across so the toggle is
     * non-destructive. cm is kept whole — [Units.cmToFtIn] round-trips whole centimetres back to the
     * same foot/inch pair, so flipping repeatedly never drifts.
     */
    fun onHeightUnit(unit: HeightUnit) {
        _state.update { s ->
            if (s.heightUnit == unit) {
                return@update s
            }
            when (unit) {
                HeightUnit.FT_IN ->
                    s.heightCm.toDoubleOrNull()?.let { cm ->
                        val (feet, inches) = Units.cmToFtIn(cm)
                        s.copy(
                            heightUnit = unit,
                            heightFt = feet.toString(),
                            heightInches = inches.toString(),
                        )
                    } ?: s.copy(heightUnit = unit)

                HeightUnit.CM ->
                    s.heightFt.toIntOrNull()?.let { feet ->
                        val cm = Units.toCm(feet, s.heightInches.toIntOrNull() ?: 0)
                        s.copy(heightUnit = unit, heightCm = cm.roundToInt().toString())
                    } ?: s.copy(heightUnit = unit)
            }
        }
        viewModelScope.launch { preferences.setHeightUnit(unit) }
    }

    fun onRaceDate(millis: Long?) = _state.update { it.copy(raceDateMillis = millis) }

    fun onGender(gender: Gender) = _state.update { it.copy(gender = gender) }

    fun onTier(tier: Tier) = _state.update { it.copy(tier = tier) }

    fun onFormat(format: RaceMode) = _state.update { it.copy(raceMode = format) }

    /**
     * Picks a place off the calendar and starts observing its races. The date is cleared first so a
     * stale date from a previously chosen city can never survive; it is then re-filled automatically
     * when the new city offers exactly one possible race day.
     */
    fun onCitySelected(city: RaceCity) {
        _state.update {
            it.copy(
                selectedCity = city,
                raceCity = city.city,
                cityEvents = emptyList(),
                raceDateMillis = null,
                manualRaceEntry = false,
            )
        }
        cityEventsJob?.cancel()
        cityEventsJob = viewModelScope.launch {
            raceCalendar.observeEventsIn(city).collect { events ->
                _state.update { s ->
                    val updated = s.copy(cityEvents = events)
                    val onlyDay = updated.availableRaceDays.singleOrNull()
                    if (onlyDay != null) updated.copy(raceDateMillis = onlyDay) else updated
                }
            }
        }
    }

    /** Chooses one day inside the selected city's race window. */
    fun onRaceDaySelected(millis: Long) = _state.update { it.copy(raceDateMillis = millis) }

    /**
     * Escape hatch for a race that isn't on the calendar — the bundled list goes stale between
     * releases, and athletes train for events the calendar never lists. Falls back to the free-text
     * city and date picker this screen used before.
     */
    fun onManualRaceEntry() {
        cityEventsJob?.cancel()
        _state.update {
            it.copy(
                manualRaceEntry = true,
                selectedCity = null,
                cityEvents = emptyList(),
                raceCity = "",
                raceDateMillis = null,
            )
        }
    }

    /** Returns to picking from the calendar, discarding whatever was typed by hand. */
    fun onUseCalendar() =
        _state.update {
            it.copy(manualRaceEntry = false, raceCity = "", raceDateMillis = null, selectedCity = null)
        }

    fun onCity(value: String) = _state.update { it.copy(raceCity = value) }

    /** Nudge bodyweight (kg) by [delta], clamped; edits the same text the field shows. */
    fun stepBodyweight(delta: Int) = _state.update { it.copy(bodyweightKg = it.bodyweightKg.step(delta, min = 0, max = 500)) }

    /** Nudge whole feet by [delta], clamped. */
    fun stepHeightFt(delta: Int) =
        _state.update {
            it.copy(heightFt = it.heightFt.step(delta, min = HeightBounds.MIN_FT, max = HeightBounds.MAX_FT))
        }

    /** Nudge inches by [delta], clamped to 0..11 — it does not carry into feet. */
    fun stepHeightInches(delta: Int) =
        _state.update {
            it.copy(heightInches = it.heightInches.step(delta, min = 0, max = HeightBounds.MAX_INCHES))
        }

    /** Nudge centimetres by [delta], clamped. */
    fun stepHeightCm(delta: Int) =
        _state.update {
            it.copy(heightCm = it.heightCm.step(delta, min = HeightBounds.MIN_CM, max = HeightBounds.MAX_CM))
        }

    fun onNext() = _state.update { if (it.isLast) it else it.copy(stepIndex = it.stepIndex + 1) }

    fun onBack() = _state.update { if (it.isFirst) it else it.copy(stepIndex = it.stepIndex - 1) }

    fun onComplete() {
        val s = _state.value
        if (!s.currentStepValid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val division = divisionKey(s.gender, s.tier)

            // Identity + baseline on the profile…
            repository.save(
                AthleteProfile(
                    fullName = s.fullName.trim(),
                    bodyweightKg = s.bodyweightKg.toDoubleOrNull(),
                    heightCm = s.heightCmValue,
                ),
            )

            // …and the target race as a first-class, multi-instance goal (drives the Home countdown).
            raceGoals.create(
                formatKey = EventFormat.HYROX,
                divisionKey = division,
                mode = s.raceMode!!,
                targetDate = s.raceDateMillis?.let(Instant::fromEpochMilliseconds),
                city = s.raceCity.trim().ifBlank { null },
            )

            preferences.setRaceInfo(
                formatKey = EventFormat.HYROX,
                gender = s.gender!!,
                divisionKey = division,
                raceMode = s.raceMode,
            )

            preferences.setOnboardingComplete(true)
            _state.update { it.copy(saving = false, done = true) }
        }
    }
}

/** UTC midnight today, matching the units M3's DatePicker hands back. */
private fun todayUtcMillis(clock: Clock): Long {
    val millisPerDay = 86_400_000L
    return clock.now().toEpochMilliseconds() / millisPerDay * millisPerDay
}

private fun divisionKey(gender: Gender?, tier: Tier?): String {
    val base = if (gender == Gender.WOMEN) "WOMEN" else "MEN"
    return if (tier == Tier.PRO) "${base}_PRO" else base
}

private fun String.sanitizeDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    return if (dot == -1) {
        filtered
    } else {
        filtered.substring(0, dot + 1) +
            filtered
                .substring(dot + 1)
                .replace(".", "")
    }
}

/** Digits only, capped at [max]. Blank stays blank so the field can be cleared. */
private fun String.sanitizeInt(max: Int): String {
    val digits = filter { it.isDigit() }.trimStart('0').ifEmpty { if (any { it.isDigit() }) "0" else "" }
    val value = digits.toIntOrNull() ?: return ""
    return if (value > max) max.toString() else digits
}

private fun String.step(delta: Int, min: Int, max: Int): String {
    val current = toDoubleOrNull()?.toInt() ?: 0
    return (current + delta).coerceIn(min, max).toString()
}
