package com.mindset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindset.components.BottomNavBar
import com.mindset.components.MindSetTopBar
import com.mindset.model.BottomNavTab
import com.mindset.presentation.ProfileUiState
import com.mindset.presentation.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(
    onTab: (BottomNavTab) -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(ProfileSheet.None) }

    MindSetTheme {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { MindSetTopBar(onProfileClick = { onTab(BottomNavTab.Profile) }) },
            bottomBar = { BottomNavBar(current = BottomNavTab.Profile, onTabClick = onTab) },
        ) { inner ->
            ProfileContent(
                state = state,
                contentPadding = inner,
                onSubscriptionClick = { sheet = if (state.isPro) ProfileSheet.Manage else ProfileSheet.Upgrade },
            )
        }

        // Which sheet is open is a pure view affordance with no domain meaning, so it stays UI-local
        // (and survives configuration change via rememberSaveable) rather than bloating
        // ProfileUiState — same reasoning as IntegrationsCard's expansion state.
        when (sheet) {
            // Customer Center and the paywall both report nothing back: a purchase or a
            // cancellation reaches the app through EntitlementRepository, so closing is all there is
            // to do here.
            ProfileSheet.Manage -> CustomerCenterSheet(onDismiss = { sheet = ProfileSheet.None })
            ProfileSheet.Upgrade -> PaywallSheet(onDismiss = { sheet = ProfileSheet.None })
            ProfileSheet.None -> Unit
        }
    }
}

/** The sheets Profile can present. Saveable, so it survives rotation with the sheet still open. */
private enum class ProfileSheet { None, Manage, Upgrade }

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    contentPadding: PaddingValues,
    onSubscriptionClick: () -> Unit,
) {
    val spacing = MaterialTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = spacing.md,
            end = spacing.md,
            top = contentPadding.calculateTopPadding() + spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (state.isLoading) return@LazyColumn

        item(key = "header") {
            ProfileHeaderCard(name = state.athleteName, tierLabel = state.tierLabel)
        }

        item(key = "stats") {
            ProfileStatsRow(streakDays = state.streakDays, totalSessions = state.totalSessions)
        }

        if (state.frequency.isNotEmpty()) {
            item(key = "frequency") { TrainingFrequencyCard(weeks = state.frequency) }
        }

        item(key = "subscription") {
            SubscriptionCard(isPro = state.isPro, onClick = onSubscriptionClick)
        }

//        item(key = "integrations") { IntegrationsCard() }
    }
}
