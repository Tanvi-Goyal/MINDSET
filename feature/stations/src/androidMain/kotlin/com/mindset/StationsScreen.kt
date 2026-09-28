package com.mindset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindset.components.BottomNavBar
import com.mindset.components.MindSetTopBar
import com.mindset.helpers.UIHelper
import com.mindset.icons.Lock
import com.mindset.icons.TrendDown
import com.mindset.icons.TrendUp
import com.mindset.model.BottomNavTab
import com.mindset.presentation.StationCardUi
import com.mindset.presentation.StationTrendUi
import com.mindset.presentation.StationsUiState
import com.mindset.presentation.StationsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StationsScreen(onTab: (BottomNavTab) -> Unit, viewModel: StationsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MindSetTheme {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MindSetTopBar(
                    onProfileClick = { onTab(BottomNavTab.Profile) },
                )
            },
            bottomBar = {
                BottomNavBar(
                    current = BottomNavTab.Stations,
                    onTabClick = onTab,
                )
            },
        ) { padding ->
            StationBoard(
                state = state,
                contentPadding = padding,
                onUnlockClick = viewModel::openPaywall,
            )
        }

        // Rides on top of a live board — the tab itself is never blocked. A completed purchase is
        // not handled here: it arrives through EntitlementRepository like any other entitlement
        // change, so this only has to close itself.
        if (state.showPaywall) {
            PaywallSheet(onDismiss = viewModel::dismissPaywall)
        }
    }
}

@Composable
private fun StationBoard(state: StationsUiState, contentPadding: PaddingValues, onUnlockClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.md,
            end = spacing.md,
            top = contentPadding.calculateTopPadding() + spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + spacing.md,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.smd),
        verticalArrangement = Arrangement.spacedBy(spacing.smd),
    ) {
        item(
            key = "header",
            span = {
                GridItemSpan(maxLineSpan)
            },
        ) {
            BoardHeader()
        }

        // Keeps the offer reachable after the metered prompt has been dismissed, so dismissing it
        // once does not hide Pro forever.
        if (!state.isPro) {
            item(key = "unlock", span = { GridItemSpan(maxLineSpan) }) {
                UnlockProCard(onClick = onUnlockClick)
            }
        }

        items(state.stations, key = { it.station.name }) { card ->
            StationCard(card)
        }

        if (!state.loading && state.stations.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyBoardHint()
            }
        }
    }
}

@Composable
private fun BoardHeader() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        Text(
            text = "Competition protocol".uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
        )
        Text(
            text = "Station Board",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Box(
            Modifier
                .padding(top = MaterialTheme.spacing.xs)
                .fillMaxWidth()
                .height(2.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.primary),
        )
    }
}

@Composable
private fun UnlockProCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.smd,
                vertical = MaterialTheme.spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MindSetIcons.Lock,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.spacing.smd),
        ) {
            Text(
                text = "MIND[SET] Pro",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "Full history and every station record",
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StationCard(card: StationCardUi) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .padding(
                horizontal = MaterialTheme.spacing.smd,
                vertical = MaterialTheme.spacing.md,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                UIHelper.hyroxStationIcon(card.station),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = card.targetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = MaterialTheme.spacing.sm),
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.station.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (card.trend != null) TrendChip(card.trend)
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        MetricRow(
            label = "Recent",
            value = card.recentLabel,
            valueColor = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
        MetricRow(label = "PB", value = card.pbLabel, valueColor = colors.primary)

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        HorizontalDivider(color = GlassBorder)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        MetricRow(label = "Sessions", value = card.sessionsLabel, valueColor = colors.onSurface)
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun TrendChip(trend: StationTrendUi) {
    val tint = if (trend.improving) TrendImprovingColor else MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        Icon(
            if (trend.improving) MindSetIcons.TrendUp else MindSetIcons.TrendDown,
            contentDescription = if (trend.improving) {
                "Faster than last session"
            } else {
                "Slower than last session"
            },
            tint = tint,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = trend.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun EmptyBoardHint() {
    val colors = MaterialTheme.colorScheme
    Text(
        text = "Set your Hyrox division in Profile to see your station board.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.md),
    )
}
