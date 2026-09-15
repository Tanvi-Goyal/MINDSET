package com.mindset.domain.repository

import com.mindset.domain.HeightUnit
import com.mindset.domain.ThemeMode
import com.mindset.domain.UserPreferences
import com.mindset.domain.WeightUnit
import com.mindset.model.Gender
import com.mindset.model.RaceMode
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the user's device-local [UserPreferences]. Implementations back this with local
 * storage; the UI observes it as a [Flow] and never sees storage details.
 */
interface PreferencesRepository {
    /** Emits the current preferences, falling back to defaults when nothing has been written yet. */
    fun observe(): Flow<UserPreferences>

    suspend fun setWeightUnit(unit: WeightUnit)

    /** Remembers how the user prefers to enter height. Storage stays cm regardless. */
    suspend fun setHeightUnit(unit: HeightUnit)

    suspend fun setThemeMode(mode: ThemeMode)

    /** Marks first-run onboarding complete (or resets it). Read back via [observe]. */
    suspend fun setOnboardingComplete(complete: Boolean)


    suspend fun setRaceInfo(formatKey: String, gender: Gender, divisionKey: String, raceMode: RaceMode)

    suspend fun getRaceInfo(): Triple<String?, Gender?, RaceMode?>

}
