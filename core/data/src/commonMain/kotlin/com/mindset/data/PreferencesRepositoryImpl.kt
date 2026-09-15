package com.mindset.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mindset.domain.HeightUnit
import com.mindset.domain.ThemeMode
import com.mindset.domain.UserPreferences
import com.mindset.domain.WeightUnit
import com.mindset.domain.repository.PreferencesRepository
import com.mindset.model.Gender
import com.mindset.model.RaceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class PreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) : PreferencesRepository {
    override fun observe(): Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            weightUnit =
            prefs[Keys.WEIGHT_UNIT]
                ?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                ?: WeightUnit.KG,
            heightUnit =
            prefs[Keys.HEIGHT_UNIT]
                ?.let { runCatching { HeightUnit.valueOf(it) }.getOrNull() }
                ?: HeightUnit.FT_IN,
            themeMode =
            prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            isOnboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            hyroxDivisionKey = prefs[Keys.HYROX_DIVISION_KEY],
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
            raceMode =
            prefs[Keys.RACE_MODE]?.let {
                runCatching {
                    RaceMode.valueOf(
                        it,
                    )
                }.getOrNull()
            },
        )
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    override suspend fun setHeightUnit(unit: HeightUnit) {
        dataStore.edit { it[Keys.HEIGHT_UNIT] = unit.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    override suspend fun setRaceInfo(formatKey: String, gender: Gender, divisionKey: String, raceMode: RaceMode) {
        dataStore.edit { prefs ->
            prefs[Keys.HYROX_DIVISION_KEY] = divisionKey
            prefs[Keys.GENDER] = gender.name
            prefs[Keys.RACE_MODE] = raceMode.name
        }
    }

    override suspend fun getRaceInfo(): Triple<String?, Gender?, RaceMode?> {
        val prefs = dataStore.data.firstOrNull()
        val divisionKey = prefs?.get(Keys.HYROX_DIVISION_KEY)
        val gender = prefs?.get(Keys.GENDER)?.let { runCatching { Gender.valueOf(it) }.getOrNull() }
        val raceMode =
            prefs?.get(Keys.RACE_MODE)?.let { runCatching { RaceMode.valueOf(it) }.getOrNull() }
        return Triple(divisionKey, gender, raceMode)
    }

    private object Keys {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val HEIGHT_UNIT = stringPreferencesKey("height_unit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val HYROX_DIVISION_KEY = stringPreferencesKey("hyrox_division_key")
        val GENDER = stringPreferencesKey("gender")
        val RACE_MODE = stringPreferencesKey("race_mode")
    }
}
