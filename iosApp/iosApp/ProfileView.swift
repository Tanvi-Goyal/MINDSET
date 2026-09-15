import SwiftUI
import Shared

/// Profile — SwiftUI counterpart of `feature/profile/src/androidMain/kotlin/com/mindset/ProfileScreen.kt`.
///
/// Three sections, matching the Compose LazyColumn: header card, a two-tile stats row, and the
/// training-frequency bar chart. Every value is computed in `ProfileViewModel` — `tierLabel` arrives
/// pre-formatted as e.g. "MEN PRO · SINGLES", the streak is already resolved against the midnight
/// ticker, and the frequency buckets are already labelled "W1".."Wn".
///
/// One deliberate deviation: Android has **no** UI anywhere for weight unit or theme — its
/// `PreferencesViewModel` is consumed only by `MainActivity` to seed composition locals. The unit
/// control at the bottom is iOS-only, kept because it is genuinely useful and already wired. Theme
/// is gone: the app is dark-only, so the picker did nothing on either platform.
struct ProfileView: View {
    @EnvironmentObject private var prefs: PreferencesStore
    @StateObject private var store = ProfileStore()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Space.lg) {
                    if let state = store.state, !state.isLoading {
                        ProfileHeaderCard(name: state.athleteName, tierLabel: state.tierLabel)
                        ProfileStatsRow(
                            streakDays: Int(state.streakDays),
                            totalSessions: Int(state.totalSessions)
                        )
                        if !state.frequency.isEmpty {
                            TrainingFrequencyCard(weeks: state.frequency)
                        }
                    }
                    UnitPreferenceCard(units: prefs.units, onChange: prefs.setUnit)
                }
                .padding(.horizontal, Space.md)
                .padding(.top, Space.sm)
                .padding(.bottom, Space.md)
            }
            .background(Obsidian.background)
            .mindSetToolbar()
        }
    }
}

// MARK: - Header

private struct ProfileHeaderCard: View {
    let name: String
    let tierLabel: String

    var body: some View {
        VStack(spacing: Space.smd) {
            // 96pt circle, 2pt accent ring, 4pt gap, neutral fill.
            Circle()
                .strokeBorder(Obsidian.primary, lineWidth: 2)
                .background(
                    Circle()
                        .fill(Obsidian.surfaceContainerHigh)
                        .padding(4)
                )
                .frame(width: 96, height: 96)
                .overlay(
                    Image(systemName: "person.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                )

            Text(name)
                .font(MSFont.headlineSmall)
                .foregroundStyle(Obsidian.onSurface)
                .lineLimit(1)

            if !tierLabel.isEmpty {
                Text(tierLabel)
                    .labelMediumStyle()
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Space.lg)
        .padding(.horizontal, Space.md)
        .glassCard()
    }
}

// MARK: - Stats

private struct ProfileStatsRow: View {
    let streakDays: Int
    let totalSessions: Int

    var body: some View {
        HStack(spacing: Space.smd) {
            StatTile(value: "\(streakDays)", label: "Day streak")
            StatTile(value: "\(totalSessions)", label: "Sessions")
        }
    }
}

private struct StatTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: Space.xs) {
            Text(value)
                .font(MSFont.headlineSmall)
                .foregroundStyle(Obsidian.primary)
            Text(label.uppercased())
                .labelSmallStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Space.md)
        .glassCard()
    }
}

// MARK: - Frequency

/// A bar chart, not a dot strip. Bars are 120pt tall at most; a week with no sessions still draws a
/// 5% sliver so the axis reads as continuous, and the in-progress week is coral rather than red.
private struct TrainingFrequencyCard: View {
    let weeks: [WeekFrequencyUi]

    private var maxCount: Int {
        max(1, weeks.map { Int($0.sessionCount) }.max() ?? 1)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.md) {
            HStack {
                Text("Training frequency".uppercased())
                    .labelMediumStyle()
                    .foregroundStyle(Obsidian.onSurfaceVariant)
                Spacer()
                Text("Active")
                    .labelSmallStyle()
                    .foregroundStyle(Obsidian.coral)
            }

            HStack(alignment: .bottom, spacing: Space.sm) {
                ForEach(Array(weeks.enumerated()), id: \.offset) { _, week in
                    FrequencyBar(week: week, maxCount: maxCount)
                }
            }
            .frame(height: 120)
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}

private struct FrequencyBar: View {
    let week: WeekFrequencyUi
    let maxCount: Int

    private var fraction: CGFloat {
        min(max(CGFloat(Int(week.sessionCount)) / CGFloat(maxCount), 0.05), 1)
    }

    var body: some View {
        VStack(spacing: Space.sm) {
            GeometryReader { geo in
                VStack {
                    Spacer(minLength: 0)
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .fill(week.isCurrent ? Obsidian.coral : Obsidian.primary.opacity(0.3))
                        .frame(height: max(geo.size.height * fraction, 2))
                }
            }
            Text(week.label)
                .labelSmallStyle()
                .fontWeight(week.isCurrent ? .bold : .regular)
                .foregroundStyle(week.isCurrent ? Obsidian.coral : Obsidian.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Preferences (iOS-only; Android exposes no control for this)

private struct UnitPreferenceCard: View {
    let units: WeightUnit
    let onChange: (WeightUnit) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            Text("Weight units".uppercased())
                .labelMediumStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
            SegmentedSelector(
                options: ["Kilograms", "Pounds"],
                selectedIndex: Int(units.ordinal)
            ) { index in
                onChange(KotlinEnums.weightUnits[index])
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}
