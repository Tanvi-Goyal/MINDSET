package com.mindset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mindset.icons.ChevronRight
import com.mindset.icons.Lock

/**
 * The athlete's subscription row.
 *
 * One card with two states rather than two cards: a subscriber needs a way into Customer Center
 * (plan, renewal date, cancellation, refunds) and a free athlete needs a way into the paywall, and
 * both are the same question — "what is my subscription?" — asked from opposite sides.
 */
@Composable
fun SubscriptionCard(isPro: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MindSetIcons.Lock,
            contentDescription = null,
            // Tinted with the accent only when subscribed, so the card reads as a status when it is
            // one and as an offer when it is not.
            tint = if (isPro) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = if (isPro) "MIND[SET] Pro" else "Upgrade to Pro",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Text(
                text = if (isPro) {
                    "Active · manage plan, billing and cancellation"
                } else {
                    "Full history and every station record"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        // The app's "this navigates" signal — see IntegrationsCard for why the placeholder rows
        // deliberately lack one.
        Icon(
            MindSetIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}
