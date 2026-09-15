import SwiftUI
import Shared

/// First-run setup, driven entirely by the shared `OnboardingViewModel`.
///
/// SwiftUI counterpart of `feature/onboarding/src/androidMain/kotlin/com/mindset/OnboardingScreen.kt`.
/// Every rule lives in Kotlin: which fields gate the step (`currentStepValid`), how bodyweight and
/// height clamp when stepped, how gender + tier become a division key (`MEN` / `MEN_PRO` / `WOMEN` /
/// `WOMEN_PRO`), and what `onComplete()` writes. This file is layout and nothing else — which is the
/// whole argument for the architecture, since the same logic gates the Compose screen.
struct OnboardingView: View {
    @StateObject private var store = OnboardingStore()
    @State private var showDatePicker = false

    var body: some View {
        ZStack(alignment: .top) {
            Obsidian.background.ignoresSafeArea()

            // Top-anchored glow, matching OnboardingBackdrop (primary @ 10% → clear, 360pt).
            RadialGradient(
                colors: [Obsidian.primary.opacity(0.10), .clear],
                center: .top,
                startRadius: 0,
                endRadius: 360
            )
            .frame(height: 360)
            .ignoresSafeArea(edges: .top)

            if let state = store.state {
                content(state)
            }
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func content(_ state: OnboardingUiState) -> some View {
        VStack(spacing: 0) {
            Spacer().frame(height: Space.lg)
            Wordmark(size: 28)
            Spacer().frame(height: Space.lg)

            StepIndicator(stepIndex: Int(state.stepIndex), stepCount: Int(state.stepCount))

            Spacer().frame(height: 28)

            ScrollView {
                // `step` is a computed property on the shared state — the step order lives in the
                // Kotlin `OnboardingStep` enum, not here.
                // Compared by `name` rather than a Swift-side enum case: Obj-C entry spellings
                // are generated, so this is the one form guaranteed to match the header.
                if state.step.name == "ATHLETE_PROFILE" {
                    athleteProfileStep(state)
                } else {
                    raceConfigStep(state)
                }
                Spacer().frame(height: Space.md)
            }
            .scrollDismissesKeyboard(.interactively)

            bottomButtons(state)
            Spacer().frame(height: Space.md)
        }
        .padding(.horizontal, Space.lg)
    }

    // MARK: Step 1

    @ViewBuilder
    private func athleteProfileStep(_ state: OnboardingUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            StepHeading(
                title: "Athlete Profile",
                subtitle: "Define your physical baseline for precise programming."
            )

            FieldLabel(text: "Full Name")
            GlassTextField(
                placeholder: "e.g. Alex Sterling",
                text: Binding(get: { state.fullName }, set: store.setName)
            )

            Spacer().frame(height: Space.md)

            HStack(alignment: .top, spacing: Space.md) {
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Bodyweight (kg)")
                    StepperField(
                        value: Binding(get: { state.bodyweightKg }, set: store.setBodyweight),
                        onStep: store.stepBodyweight
                    )
                }
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Height (in)")
                    StepperField(
                        value: Binding(get: { state.heightIn }, set: store.setHeight),
                        onStep: store.stepHeight
                    )
                }
            }
        }
    }

    // MARK: Step 2

    @ViewBuilder
    private func raceConfigStep(_ state: OnboardingUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            StepHeading(
                title: "Race Configuration",
                subtitle: "Set your sights on the finish line."
            )

            FieldLabel(text: "Competition Date")
            Button {
                showDatePicker = true
            } label: {
                HStack {
                    Text(Self.dateText(state.raceDateMillis))
                        .font(MSFont.bodyLarge)
                        .foregroundStyle(state.raceDateMillis == nil
                                         ? Obsidian.onSurfaceVariant.opacity(0.6)
                                         : Obsidian.onSurface)
                    Spacer()
                    Image(systemName: "calendar")
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                }
                .padding(.horizontal, Space.md)
                .padding(.vertical, Space.smd)
                .glassCard()
            }
            .buttonStyle(.plain)

            Spacer().frame(height: Space.md)

            // Each selector maps its label list onto the Kotlin enum by ordinal, exactly as the
            // Compose screen does — the enums carry no display names.
            FieldLabel(text: "Category")
            SegmentedSelector(
                options: ["Women", "Men"],
                selectedIndex: state.gender.map { Int($0.ordinal) }
            ) { store.setGender(KotlinEnums.genders[$0]) }

            Spacer().frame(height: Space.md)

            FieldLabel(text: "Division")
            SegmentedSelector(
                options: ["Open", "Pro"],
                selectedIndex: state.tier.map { Int($0.ordinal) }
            ) { store.setTier(KotlinEnums.tiers[$0]) }

            Spacer().frame(height: Space.md)

            FieldLabel(text: "Format")
            SegmentedSelector(
                options: ["Singles", "Doubles", "Relay"],
                selectedIndex: state.raceMode.map { Int($0.ordinal) }
            ) { store.setFormat(KotlinEnums.raceModes[$0]) }

            Spacer().frame(height: Space.md)

            FieldLabel(text: "Target Race City")
            GlassTextField(
                placeholder: "e.g., Stockholm",
                text: Binding(get: { state.raceCity }, set: store.setCity)
            )
        }
        .sheet(isPresented: $showDatePicker) {
            DatePickerSheet(
                initialMillis: state.raceDateMillis?.int64Value,
                onCancel: { showDatePicker = false },
                onConfirm: { millis in
                    store.setRaceDate(millis)
                    showDatePicker = false
                }
            )
        }
    }

    // MARK: Buttons

    @ViewBuilder
    private func bottomButtons(_ state: OnboardingUiState) -> some View {
        HStack(spacing: Space.smd) {
            if state.isFirst {
                PrimaryButton(
                    title: "Next Configuration",
                    enabled: state.currentStepValid && !state.saving,
                    action: store.next
                )
            } else {
                // Compose splits these 1:2, so Complete reads as the primary action.
                SecondaryButton(title: "Back", action: store.back)
                    .frame(maxWidth: .infinity)
                PrimaryButton(
                    title: "Complete Setup",
                    enabled: state.currentStepValid && !state.saving,
                    action: store.complete
                )
                .frame(maxWidth: .infinity)
                .layoutPriority(1)
            }
        }
    }

    private static func dateText(_ millis: KotlinLong?) -> String {
        guard let millis else { return "mm / dd / yyyy" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: Date(timeIntervalSince1970: Double(millis.int64Value) / 1000))
    }
}

// MARK: - Pieces

private struct StepIndicator: View {
    let stepIndex: Int
    let stepCount: Int

    var body: some View {
        VStack(spacing: 10) {
            Text(String(format: "STEP %02d/%02d", stepIndex + 1, stepCount))
                .labelMediumStyle()
                .foregroundStyle(Obsidian.primary)
            HStack(spacing: Space.sm) {
                ForEach(0..<stepCount, id: \.self) { index in
                    Capsule()
                        .fill(index <= stepIndex ? Obsidian.primary : Obsidian.surfaceContainerHigh)
                        .frame(height: 2)
                }
            }
        }
    }
}

private struct StepHeading: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            Text(title)
                .font(MSFont.headlineSmall)
                .foregroundStyle(Obsidian.onSurface)
            Text(subtitle)
                .font(MSFont.bodyMedium)
                .foregroundStyle(Obsidian.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, Space.lg)
    }
}

private struct DatePickerSheet: View {
    let initialMillis: Int64?
    let onCancel: () -> Void
    let onConfirm: (Int64) -> Void

    @State private var date: Date

    init(initialMillis: Int64?, onCancel: @escaping () -> Void, onConfirm: @escaping (Int64) -> Void) {
        self.initialMillis = initialMillis
        self.onCancel = onCancel
        self.onConfirm = onConfirm
        _date = State(initialValue: initialMillis.map {
            Date(timeIntervalSince1970: Double($0) / 1000)
        } ?? Date())
    }

    var body: some View {
        NavigationStack {
            DatePicker("Competition date", selection: $date, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(Obsidian.primary)
                .padding(Space.md)
                .frame(maxHeight: .infinity, alignment: .top)
                .background(Obsidian.background)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", action: onCancel).tint(Obsidian.onSurfaceVariant)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("OK") { onConfirm(Int64(date.timeIntervalSince1970 * 1000)) }
                            .tint(Obsidian.primary)
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }
}
