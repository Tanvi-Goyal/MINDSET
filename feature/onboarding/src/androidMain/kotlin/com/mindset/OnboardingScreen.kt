package com.mindset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindset.components.FieldLabel
import com.mindset.components.GlassCard
import com.mindset.components.GlassTextField
import com.mindset.components.PrimaryButton
import com.mindset.components.SecondaryButton
import com.mindset.domain.HeightUnit
import com.mindset.icons.ChevronRight
import com.mindset.model.Gender
import com.mindset.model.RaceCity
import com.mindset.model.RaceMode
import com.mindset.model.Tier
import com.mindset.presentation.OnboardingStep
import com.mindset.presentation.OnboardingUiState
import com.mindset.presentation.OnboardingViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onComplete() }

    MindSetTheme {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(colors.background)) {
            OnboardingBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = MaterialTheme.spacing.lg),
            ) {
                Spacer(Modifier.height(MaterialTheme.spacing.lg))
                AppBrand(
                    modifier = Modifier.fillMaxWidth(),
                    logoSize = 60.dp,
                    showWordmark = false,
                )
                Spacer(Modifier.height(MaterialTheme.spacing.lg))
                StepIndicator(stepIndex = state.stepIndex, stepCount = state.stepCount)
                Spacer(Modifier.height(28.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (state.step) {
                        OnboardingStep.ATHLETE_PROFILE -> AthleteProfileStep(state, viewModel)
                        OnboardingStep.RACE_CONFIG -> RaceConfigStep(state, viewModel)
                    }
                    Spacer(Modifier.height(MaterialTheme.spacing.md))
                }

                BottomButtons(state, viewModel)
                Spacer(Modifier.height(MaterialTheme.spacing.md))
            }
        }
    }
}

@Composable
private fun OnboardingBackdrop() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.10f), Color.Transparent),
                ),
            ),
    )
}

@Composable
private fun StepIndicator(stepIndex: Int, stepCount: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "STEP %02d/%02d".format(stepIndex + 1, stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            repeat(stepCount) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= stepIndex) {
                                colors.primary
                            } else {
                                colors.surfaceContainerHigh
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
    }
}

@Composable
private fun AthleteProfileStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    Column {
        StepHeading(
            "Athlete Profile",
            "Define your physical baseline for precise programming.",
        )
        FieldLabel("Full Name")
        GlassTextField(
            value = state.fullName,
            onValueChange = vm::onFullName,
            placeholder = "e.g. Alex Sterling",
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        StepperField(
            label = "Bodyweight (kg)",
            value = state.bodyweightKg,
            onValueChange = vm::onBodyweight,
            onDecrement = { vm.stepBodyweight(-1) },
            onIncrement = { vm.stepBodyweight(+1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        FieldLabel("Height")
        SegmentedSelector(
            options = listOf("ft / in", "cm"),
            selectedIndex = state.heightUnit.ordinal,
            onSelect = { vm.onHeightUnit(HeightUnit.entries[it]) },
        )
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            when (state.heightUnit) {
                HeightUnit.FT_IN -> {
                    com.mindset.components.StepperField(
                        value = state.heightFt,
                        onValueChange = vm::onHeightFt,
                        onStep = vm::stepHeightFt,
                        caption = "Feet",
                        modifier = Modifier.weight(1f),
                    )
                    com.mindset.components.StepperField(
                        value = state.heightInches,
                        onValueChange = vm::onHeightInches,
                        onStep = vm::stepHeightInches,
                        caption = "Inches",
                        modifier = Modifier.weight(1f),
                    )
                }

                HeightUnit.CM -> {
                    com.mindset.components.StepperField(
                        value = state.heightCm,
                        onValueChange = vm::onHeightCm,
                        onStep = vm::stepHeightCm,
                        caption = "Centimetres",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaceConfigStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }

    Column {
        StepHeading("Race Configuration", "Set your sights on the finish line.")

        if (state.manualRaceEntry) {
            FieldLabel("Target Race City")
            GlassTextField(
                value = state.raceCity,
                onValueChange = vm::onCity,
                placeholder = "e.g., Stockholm",
            )
            LinkButton("Pick from the race calendar", vm::onUseCalendar)
        } else {
            FieldLabel("Target Race")
            PickerRow(
                text = state.selectedCity?.label ?: "Find your race",
                isPlaceholder = state.selectedCity == null,
                onClick = { showCityPicker = true },
            )
            LinkButton("My race isn't listed", vm::onManualRaceEntry)
        }
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        FieldLabel("Race Day")
        RaceDayField(
            state = state,
            onPickManualDate = { showDatePicker = true },
            onSelectDay = vm::onRaceDaySelected,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        FieldLabel("Category")
        SegmentedSelector(
            options = listOf("Women", "Men"),
            selectedIndex = state.gender?.ordinal ?: -1,
            onSelect = { vm.onGender(Gender.entries[it]) },
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        FieldLabel("Division")
        SegmentedSelector(
            options = listOf("Open", "Pro"),
            selectedIndex = state.tier?.ordinal ?: -1,
            onSelect = { vm.onTier(Tier.entries[it]) },
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        FieldLabel("Format")
        SegmentedSelector(
            options = listOf("Singles", "Doubles", "Relay"),
            selectedIndex = state.raceMode?.ordinal ?: -1,
            onSelect = { vm.onFormat(RaceMode.entries[it]) },
        )
    }

    if (showCityPicker) {
        CityPickerSheet(
            cities = state.cities,
            onDismiss = { showCityPicker = false },
            onSelect = {
                vm.onCitySelected(it)
                showCityPicker = false
            },
        )
    }

    if (showDatePicker) {
        // Manual entry only. A race you are training *for* cannot be in the past, so past days are
        // not selectable at all rather than rejected after the fact.
        val today = state.todayUtcMillis
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.raceDateMillis,
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.onRaceDate(pickerState.selectedDateMillis)
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The Race Day field has four shapes, because how the date is chosen depends entirely on what the
 * calendar knows about the selected city: hand-entered, not-yet-answerable, already decided, or a
 * short list of the days that city actually races.
 */
@Composable
private fun RaceDayField(state: OnboardingUiState, onPickManualDate: () -> Unit, onSelectDay: (Long) -> Unit) {
    val days = state.availableRaceDays
    when {
        state.manualRaceEntry ->
            PickerRow(
                text = state.raceDateMillis?.let(::formatDate) ?: "mm / dd / yyyy",
                isPlaceholder = state.raceDateMillis == null,
                onClick = onPickManualDate,
            )

        state.selectedCity == null ->
            PickerRow(text = "Choose your race first", isPlaceholder = true, onClick = null)

        // One possible day — already filled in by the ViewModel, so just show it.
        days.size == 1 ->
            PickerRow(text = state.raceDateMillis?.let(::formatDate) ?: "—", isPlaceholder = false, onClick = null)

        // A race weekend spans several days of heats; the athlete picks the one they're racing.
        else ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                days.forEach { day ->
                    DayOption(
                        label = formatDate(day),
                        selected = day == state.raceDateMillis,
                        onClick = { onSelectDay(day) },
                    )
                }
            }
    }
}

@Composable
private fun DayOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.16f) else GlassFill)
            .border(1.dp, if (selected) colors.primary else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.smd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.primary else colors.onSurface,
        )
    }
}

/** A tappable glass row that opens a picker, with the chevron affordance. */
@Composable
private fun PickerRow(text: String, isPlaceholder: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(
                    imageVector = MindSetIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A low-emphasis inline action — used for the two escape hatches under the race field. */
@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Searchable list of every place on the calendar with an upcoming race. Backed entirely by the
 * database, so it works with no connectivity — the bundled calendar is always there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPickerSheet(cities: List<RaceCity>, onDismiss: () -> Unit, onSelect: (RaceCity) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(cities, query) {
        if (query.isBlank()) cities else cities.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.85f)
                .padding(horizontal = MaterialTheme.spacing.lg),
        ) {
            Text(
                "Find your race",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.md))
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search city or country",
            )
            Spacer(Modifier.height(MaterialTheme.spacing.md))

            if (filtered.isEmpty()) {
                Text(
                    if (cities.isEmpty()) "No races on the calendar yet." else "No race matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    items(filtered, key = { it.label }) { city ->
                        PickerRow(text = city.label, isPlaceholder = false, onClick = { onSelect(city) })
                    }
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }
}

@Composable
private fun BottomButtons(state: OnboardingUiState, vm: OnboardingViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smd),
    ) {
        if (!state.isFirst) {
            SecondaryButton(text = "Back", onClick = vm::onBack, modifier = Modifier.weight(1f))
        }

        PrimaryButton(
            text = if (state.isLast) "Complete Setup" else "Next Configuration",
            enabled = state.currentStepValid && !state.saving,
            onClick = { if (state.isLast) vm.onComplete() else vm.onNext() },
            modifier = Modifier.weight(if (state.isFirst) 1f else 2f),
        )
    }
}

@Composable
private fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FieldLabel(label)
        com.mindset.components.StepperField(
            value = value,
            onValueChange = onValueChange,
            onStep = { if (it < 0) onDecrement() else onIncrement() },
        )
    }
}

@Composable
private fun SegmentedSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .padding(MaterialTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = MaterialTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Race dates are epoch millis at **UTC midnight** (what both the calendar and M3's DatePicker
 * produce), so the formatter must read them in UTC too — formatting in the device zone renders the
 * previous day anywhere west of Greenwich. Same reason `WeekCalendar` pins UTC.
 */
private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
