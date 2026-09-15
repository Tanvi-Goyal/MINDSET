package com.mindset.domain

import com.mindset.model.Gender
import com.mindset.model.RaceMode
import kotlin.math.roundToInt

/** Weight unit the UI displays loads/volume in. Storage/compute is always kg; this is display-only. */
enum class WeightUnit { KG, LB }

/** Height unit the UI captures height in. Storage is always cm; this is entry/display-only. */
enum class HeightUnit { FT_IN, CM }

/** App theme preference. SYSTEM follows the OS light/dark setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-tunable preferences. Device-local (never synced). */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightUnit: HeightUnit = HeightUnit.FT_IN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isOnboardingComplete: Boolean = false,
    val hyroxDivisionKey: String? = null,
    val gender: Gender? = null,
    val raceMode: RaceMode? = null,
)

/**
 * Weight + height conversion and labelling. Loads are stored and computed in **kg** and heights in
 * **cm**; this converts to the user's display unit (and back for input) so the conversion math lives
 * once, shared by both platforms.
 */
object Units {
    private const val LB_PER_KG = 2.2046226218
    private const val CM_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12

    /** kg (canonical) → the value shown in [unit]. */
    fun toDisplay(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg * LB_PER_KG
    }

    /** A value the user typed in [unit] → kg (canonical) for storage. */
    fun toKg(input: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> input
        WeightUnit.LB -> input / LB_PER_KG
    }

    fun label(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB -> "lb"
    }

    /** Feet + inches the user typed → cm (canonical) for storage. */
    fun toCm(feet: Int, inches: Int): Double = (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

    /**
     * cm (canonical) → whole feet and whole inches, rounded to the nearest inch and carried so the
     * inches part is always in `0..11`. Round-trips with [toCm]: `cmToFtIn(toCm(f, i)) == f to i`
     * for every whole-inch height, which is what makes the unit toggle non-destructive.
     */
    fun cmToFtIn(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt().coerceAtLeast(0)
        return totalInches / INCHES_PER_FOOT to totalInches % INCHES_PER_FOOT
    }

    fun heightLabel(unit: HeightUnit): String = when (unit) {
        HeightUnit.FT_IN -> "ft / in"
        HeightUnit.CM -> "cm"
    }
}
