package com.mindset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter

/**
 * Presents RevenueCat's Customer Center — the athlete's own subscription management surface:
 * current plan, renewal date, cancellation, plan changes, refund requests and promotional "don't
 * go" offers.
 *
 * Worth having rather than hand-building: cancellation and refund flows are mostly store-policy
 * plumbing with very little product value, and getting them subtly wrong shows up as support mail
 * rather than as a crash. This ships in the same `purchases-kmp-ui` artifact as the paywall, so it
 * costs no new dependency.
 *
 * Like [PaywallSheet] this reports nothing back: a cancellation reaches the app through the SDK
 * delegate → `EntitlementSyncer` → Room, the same path a renewal or a lapse takes.
 *
 * Any promotional offers it shows must also be configured in Google Play Console / App Store
 * Connect — the dashboard alone is not enough for those.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCenterSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxSize()) {
            CustomerCenter(onDismiss = onDismiss)
        }
    }
}
