package com.mindset.domain

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The onboarding height toggle converts the typed value across units on every flip. If that round
 * trip is lossy the user's height silently drifts each time they tap, so it is pinned here.
 */
class UnitsHeightTest {
    @Test
    fun ft_in_round_trips_through_exact_cm_for_every_plausible_height() {
        for (feet in 3..8) {
            for (inches in 0..11) {
                val cm = Units.toCm(feet, inches)
                assertEquals(feet to inches, Units.cmToFtIn(cm), "exact round trip failed for $feet'$inches\"")
            }
        }
    }

    @Test
    fun ft_in_round_trips_through_whole_cm_which_is_what_the_toggle_stores() {
        // onHeightUnit rounds to whole centimetres; every foot/inch pair must survive that too.
        for (feet in 3..8) {
            for (inches in 0..11) {
                val wholeCm = Units.toCm(feet, inches).roundToInt().toDouble()
                assertEquals(feet to inches, Units.cmToFtIn(wholeCm), "whole-cm round trip failed for $feet'$inches\"")
            }
        }
    }

    @Test
    fun inches_carry_into_feet_and_never_exceed_eleven() {
        assertEquals(6 to 0, Units.cmToFtIn(182.88)) // exactly 72"
        assertEquals(6 to 0, Units.cmToFtIn(182.5)) // 71.85" rounds up to 72", carries
        assertEquals(5 to 11, Units.cmToFtIn(180.0)) // 70.87" rounds to 71"
    }

    @Test
    fun toCm_matches_the_inch_definition() {
        assertEquals(0.0, Units.toCm(0, 0))
        assertEquals(2.54, Units.toCm(0, 1))
        assertEquals(30.48, Units.toCm(1, 0))
        assertEquals(180.34, Units.toCm(5, 11))
    }

    @Test
    fun negative_or_nonsense_cm_clamps_to_zero_rather_than_negative_inches() {
        assertEquals(0 to 0, Units.cmToFtIn(-10.0))
    }

    @Test
    fun height_labels_are_stable() {
        assertEquals("ft / in", Units.heightLabel(HeightUnit.FT_IN))
        assertEquals("cm", Units.heightLabel(HeightUnit.CM))
    }
}
