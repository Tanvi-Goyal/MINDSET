@file:OptIn(ExperimentalTime::class)

package com.mindset.presentation

import androidx.compose.runtime.Immutable
import com.mindset.domain.HeightUnit
import com.mindset.domain.Units
import com.mindset.model.Gender
import com.mindset.model.RaceCity
import com.mindset.model.RaceEvent
import com.mindset.model.RaceMode
import com.mindset.model.Tier
import kotlin.time.ExperimentalTime

/**
 * Flat MVI state for the onboarding flow. `@Immutable` is sound because every emission is rebuilt
 * via `copy` and never mutated in place.
 *
 * Height is held as **both** representations ([heightFt] + [heightInches] and [heightCm]) rather than
 * one canonical number, so flipping [heightUnit] back and forth never destroys what the user typed.
 * [heightCmValue] is the single value that reaches storage.
 */
@Immutable
data class OnboardingUiState(
    val stepIndex: Int = 0,
    val fullName: String = "",
    val bodyweightKg: String = "",
    val heightUnit: HeightUnit = HeightUnit.FT_IN,
    val heightFt: String = "",
    val heightInches: String = "",
    val heightCm: String = "",
    val raceDateMillis: Long? = null,
    val gender: Gender? = null,
    val tier: Tier? = null,
    val raceMode: RaceMode? = null,
    /** Every place on the calendar with an upcoming race — the city picker's contents. */
    val cities: List<RaceCity> = emptyList(),
    val selectedCity: RaceCity? = null,
    /** The chosen city's upcoming races; a city can host more than one. */
    val cityEvents: List<RaceEvent> = emptyList(),
    /** True when the athlete's race isn't on the calendar and they're entering it by hand. */
    val manualRaceEntry: Boolean = false,
    val raceCity: String = "",
    /** UTC midnight today — the floor for a valid race date. Seeded from the clock at init. */
    val todayUtcMillis: Long = 0L,
    val saving: Boolean = false,
    /** Set once the profile is persisted; the UI observes this to leave onboarding. */
    val done: Boolean = false,
) {
    val step: OnboardingStep get() = OnboardingStep.entries[stepIndex]
    val stepCount: Int get() = OnboardingStep.entries.size
    val isFirst: Boolean get() = stepIndex == 0
    val isLast: Boolean get() = stepIndex == OnboardingStep.entries.lastIndex

    /** Height in cm (canonical) for whichever unit is active, or null when nothing usable is typed. */
    val heightCmValue: Double?
        get() = when (heightUnit) {
            HeightUnit.FT_IN ->
                heightFt.toIntOrNull()?.let { feet ->
                    Units.toCm(feet, heightInches.toIntOrNull() ?: 0)
                }

            HeightUnit.CM -> heightCm.toDoubleOrNull()
        }?.takeIf { it in HeightBounds.MIN_CM.toDouble()..HeightBounds.MAX_CM.toDouble() }

    /**
     * Every day the athlete could be racing in the chosen city, flattened across that city's events
     * and de-duplicated. HYROX weekends run heats over several days, so picking a city usually
     * narrows the date to a handful of choices rather than one.
     */
    val availableRaceDays: List<Long>
        get() = cityEvents
            .flatMap { event -> event.raceDays.map { it.toEpochMilliseconds() } }
            .distinct()
            .sorted()

    /** Whether the current step's required fields are filled (gates Next / Complete). */
    val currentStepValid: Boolean
        get() =
            when (step) {
                OnboardingStep.ATHLETE_PROFILE -> {
                    fullName.isNotBlank()
                }

                OnboardingStep.RACE_CONFIG -> {
                    raceDateMillis != null &&
                        raceDateMillis >= todayUtcMillis &&
                        gender != null &&
                        tier != null &&
                        raceMode != null
                }
            }
}

/**
 * Plausible human-height limits. Typed text is clamped to the maxima only (clamping the minimum on
 * every keystroke would make "190" unreachable — you can never type the leading "1"); the minimum is
 * enforced once, by [OnboardingUiState.heightCmValue], so out-of-range input is simply not stored.
 */
internal object HeightBounds {
    const val MIN_FT = 3
    const val MAX_FT = 8
    const val MAX_INCHES = 11
    const val MIN_CM = 90
    const val MAX_CM = 250
}
