package com.mindset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

/**
 * Presents the RevenueCat-hosted paywall for the current offering.
 *
 * The content is authored in the RevenueCat dashboard rather than here, so pricing, copy and
 * layout change without shipping a release — which is the whole reason for preferring it over a
 * hand-rolled screen.
 *
 * Deliberately fire-and-forget: a completed purchase is **not** reported back through [onDismiss].
 * It reaches the app the same way a renewal or a refund does — SDK delegate → `EntitlementSyncer` →
 * Room → every screen observing `EntitlementRepository`. One path for every entitlement change
 * means no screen can disagree with another about whether the athlete is Pro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The paywall paints its own full-bleed background; a container colour or drag handle here
        // would frame it in a second, mismatched surface.
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        val options = remember(onDismiss) {
            PaywallOptions(dismissRequest = onDismiss) {
                // Without this the sheet can only be dismissed by swiping — fine on a phone, but
                // the paywall is the one screen where a visible way out is not optional.
                shouldDisplayDismissButton = true
            }
        }
        // Paywall() takes no modifier of its own, so the fill has to come from a wrapper.
        Box(Modifier.fillMaxSize()) { Paywall(options = options) }
    }
}
