import SwiftUI
import Shared

/// Session Detail — SwiftUI counterpart of
/// `feature/history/src/androidMain/kotlin/com/mindset/SessionDetailScreen.kt`.
///
/// Fully read-only: the only two interactive elements on the whole screen are the back button and the
/// "show remaining stations" toggle. No edit, delete, repeat or share; no dialogs.
///
/// Note there is **no toolbar here** — the back affordance is a round button in the content that
/// scrolls away with the page, unlike every other screen in the app.
struct SessionDetailView: View {
    let sessionId: String
    let onBack: () -> Void

    @EnvironmentObject private var prefs: PreferencesStore
    @StateObject private var store: SessionDetailStore
    @State private var expanded = false

    /// `STATION_LIMIT` in SessionDetailScreen.kt.
    private static let stationLimit = 4

    init(sessionId: String, onBack: @escaping () -> Void) {
        self.sessionId = sessionId
        self.onBack = onBack
        _store = StateObject(wrappedValue: SessionDetailStore(sessionId: sessionId))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.lg) {
                header
                statRow
                SectionHeader(title: store.hasTimeline ? "Race Timeline" : "Exercises")

                if store.items.isEmpty {
                    Text("No exercises logged.")
                        .font(MSFont.bodyMedium)
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                } else if store.hasTimeline {
                    timeline
                } else {
                    ForEach(store.items, id: \.loggedItemId) { item in
                        ExerciseCard(item: item, unit: prefs.units)
                    }
                }
            }
            .padding(.horizontal, Space.md)
            .padding(.top, Space.smd)
            .padding(.bottom, Space.lg)
        }
        .background(Obsidian.background)
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Header

    @ViewBuilder
    private var header: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            HeaderIconButton(systemName: "arrow.backward", label: "Back", action: onBack)
                .padding(.bottom, Space.sm)

            Text(metaLine)
                .labelMediumStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)

            Text(store.name.isEmpty ? "Session" : store.name)
                .font(MSFont.headlineSmall)
                .foregroundStyle(Obsidian.onSurface)
        }
    }

    /// Type · relative date · total time, joined with a wide middot. The time part is dropped
    /// entirely when nothing was timed, rather than showing a dash.
    private var metaLine: String {
        var parts = [
            SessionIcons.typeLabel(store.typeName).uppercased(),
            Format.relativeDay(store.startedAtMillis),
        ]
        if let ms = store.totalTimeMs { parts.append(Format.clockMs(ms)) }
        return parts.joined(separator: "  ·  ")
    }

    // MARK: Stats

    @ViewBuilder
    private var statRow: some View {
        HStack(spacing: Space.md) {
            DetailStatTile(
                value: store.totalTimeMs.map(Format.clockMs) ?? "—",
                label: "Total Time"
            )
            DetailStatTile(
                value: Format.volume(store.totalVolumeKg, prefs.units),
                label: "Volume"
            )
        }
    }

    // MARK: Timeline

    @ViewBuilder
    private var timeline: some View {
        let numbers = store.stationNumbers
        let visible = expanded ? store.items : Array(store.items.prefix(cutoff))
        let remaining = max(store.items.count - cutoff, 0)

        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(visible.enumerated()), id: \.element.loggedItemId) { index, item in
                TimelineRow(
                    item: item,
                    stationNumber: numbers[item.loggedItemId],
                    isFirst: index == 0,
                    isLast: index == visible.count - 1
                )
            }

            if remaining > 0 || expanded {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                } label: {
                    HStack(spacing: Space.sm) {
                        Text(expanded ? "Show Less" : "View \(remaining) Remaining Stations")
                            .labelMediumStyle()
                            .foregroundStyle(Obsidian.primary)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Obsidian.primary)
                            .rotationEffect(.degrees(expanded ? 270 : 90))
                    }
                    .padding(.top, Space.sm)
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// Index just past the 4th station, so runs between stations stay visible.
    private var cutoff: Int {
        var stations = 0
        for (index, item) in store.items.enumerated() {
            if let key = item.segmentKey, !key.contains("run") {
                stations += 1
                if stations == Self.stationLimit { return index + 1 }
            }
        }
        return store.items.count
    }
}

// MARK: - Pieces

private struct DetailStatTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text(label.uppercased())
                .labelSmallStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
            Text(value)
                .font(MSFont.headlineMedium)
                .foregroundStyle(Obsidian.onSurface)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Space.md)
        .glassCard()
    }
}

/// One item on the race timeline: a gutter with a rail and an optional dot, then the body.
private struct TimelineRow: View {
    let item: LoggedItemUi
    let stationNumber: Int?
    let isFirst: Bool
    let isLast: Bool

    private var isRun: Bool { item.segmentKey?.contains("run") ?? false }

    var body: some View {
        HStack(alignment: .top, spacing: Space.sm) {
            TimelineGutter(showsDot: stationNumber != nil || isFirst, isFirst: isFirst, isLast: isLast)

            VStack(alignment: .leading, spacing: Space.xs) {
                HStack(spacing: Space.sm) {
                    Image(systemName: SessionIcons.stationSymbol(item.segmentKey))
                        .font(.system(size: isRun ? 14 : 18))
                        .foregroundStyle(isRun ? Obsidian.onSurfaceVariant : Obsidian.primary)
                    Text(item.exerciseName)
                        .font(MSFont.titleSmall)
                        .foregroundStyle(Obsidian.onSurface)
                        .lineLimit(1)
                    Spacer(minLength: Space.sm)
                    if let number = stationNumber {
                        Text("STATION \(number)")
                            .labelSmallStyle()
                            .foregroundStyle(Obsidian.outline)
                    }
                }

                HStack(spacing: Space.sm) {
                    // The timeline formats its own line rather than calling the shared
                    // `detailSummary` — it shows one split per *item*, not per set, and uses the
                    // zero-padded clock. Matching Android means keeping both spellings.
                    Text(Self.splitText(item.sets.first))
                        .font(MSFont.bodyMedium.weight(.bold))
                        .foregroundStyle(Obsidian.onSurface)
                    if let context = Self.segmentContext(item.sets.first) {
                        Text(context)
                            .labelSmallStyle()
                            .foregroundStyle(Obsidian.onSurfaceVariant)
                    }
                }
            }
            .padding(.bottom, Space.smd)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(isRun ? Obsidian.surfaceContainerLow.opacity(0.0) : .clear)
    }

    private static func splitText(_ set: SetEntry?) -> String {
        guard let seconds = set?.timeSec else { return "—" }
        return Format.clockMs(Int64(truncating: seconds) * 1000)
    }

    private static func segmentContext(_ set: SetEntry?) -> String? {
        guard let set else { return nil }
        if let metres = set.distanceM ?? set.targetDistanceM { return "\(Int(truncating: metres)) m" }
        if let reps = set.reps ?? set.targetReps { return "\(Int(truncating: reps)) reps" }
        return nil
    }
}

private struct TimelineGutter: View {
    let showsDot: Bool
    let isFirst: Bool
    let isLast: Bool

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                Rectangle()
                    .fill(isFirst ? Color.clear : Obsidian.outlineVariant)
                    .frame(width: 2)
                Rectangle()
                    .fill(isLast ? Color.clear : Obsidian.outlineVariant)
                    .frame(width: 2)
            }
            if showsDot {
                Circle()
                    .fill(Obsidian.primary)
                    .frame(width: 16, height: 16)
            }
        }
        .frame(width: 32)
    }
}

/// The non-timeline body: one card per exercise, with one line per set from the **shared** formatter.
private struct ExerciseCard: View {
    let item: LoggedItemUi
    let unit: WeightUnit

    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            HStack {
                Text(item.exerciseName)
                    .font(MSFont.titleSmall)
                    .foregroundStyle(Obsidian.onSurface)
                Spacer(minLength: Space.sm)
                Text("\(item.sets.count) sets")
                    .labelSmallStyle()
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }

            ForEach(item.sets, id: \.id) { set in
                // `detailSummary` is a Kotlin extension on SetEntry, so it arrives as an Obj-C
                // category and is called as a method here. `captureFields` — a six-case sealed
                // interface — is passed straight back through without Swift ever inspecting it: the
                // `when` runs inside Kotlin, so there is no cast ladder and no missing exhaustiveness.
                Text(set.detailSummary(capture: item.captureFields, unit: unit))
                    .font(MSFont.bodyMedium)
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}
