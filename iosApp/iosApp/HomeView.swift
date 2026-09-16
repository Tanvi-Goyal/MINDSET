import SwiftUI
import Shared

/// Home — an ordered list of widgets, rendered from `HomeStore.slots`.
///
/// SwiftUI counterpart of `feature/home/src/androidMain/kotlin/com/mindset/HomeScreen.kt` and the
/// card files beside it. Android lays these out in a 2-column `LazyVerticalGrid` where Race Goal and
/// Performance take one cell each and everything else spans; SwiftUI's `LazyVGrid` has no per-item
/// span, so consecutive compact widgets are grouped into an `HStack` instead.
///
/// The `switch` is exhaustive with no `default`, which is only possible because `HomeStore` already
/// converted Kotlin's nested sealed interfaces into a Swift enum.
///
/// A note on what is *not* a bug: with an empty database this screen shows three cards, not five.
/// `HomeViewModel.raceGoalSlot()` emits nil when there is no upcoming race and `liveWorkoutSlot()`
/// emits nil when nothing is running, and `listOfNotNull` drops both. Android behaves identically.
/// Destinations pushed from Home. History is reached from "See all" and Session Detail from a row —
/// matching `MindSetNavHost.kt`, where both are pushes and neither is a tab.
private enum HomeRoute: Hashable {
    case history
    case sessionDetail(String)
}

struct HomeView: View {
    private let onOpenProfile: (() -> Void)?

    @StateObject private var store = HomeStore()
    @State private var path: [HomeRoute] = []

    // Explicit, because a `private` stored property (the @StateObject) would otherwise make the
    // synthesized memberwise initializer private too — and ContentView needs to call it.
    init(onOpenProfile: (() -> Void)? = nil) {
        self.onOpenProfile = onOpenProfile
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.lg) {
                    ForEach(rows, id: \.id) { row in
                        if row.slots.count == 1, let slot = row.slots.first {
                            widget(for: slot)
                        } else {
                            // Two equal columns, exactly like Compose's GridCells.Fixed(2).
                            // An HStack of `.frame(maxWidth: .infinity)` children is NOT the same
                            // thing: SwiftUI divides the space by each child's content flexibility,
                            // so the Race Day and This Week cards end up different widths whenever
                            // their text differs. `GridItem(.flexible())` divides the track evenly
                            // and is the real analogue.
                            LazyVGrid(columns: Self.compactColumns, alignment: .leading, spacing: Space.smd) {
                                ForEach(row.slots) { slot in
                                    widget(for: slot)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, Space.md)
                .padding(.top, Space.sm)
                .padding(.bottom, Space.md)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Obsidian.background)
            .mindSetToolbar(onOpenProfile: onOpenProfile)
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .history:
                    HistoryView(
                        onOpenDetail: { path.append(.sessionDetail($0)) },
                        onOpenProfile: onOpenProfile
                    )
                case .sessionDetail(let id):
                    SessionDetailView(sessionId: id) { path.removeLast() }
                }
            }
        }
    }

    @ViewBuilder
    private func widget(for slot: HomeSlot) -> some View {
        switch slot.content {
        case .loading:
            EmptyView()
        case .failed(let message):
            HomePlaceholder(message: message)
        case .liveWorkout:
            if let workout = store.activeWorkout {
                LiveWorkoutCard(
                    workout: workout,
                    onTogglePause: store.togglePause,
                    onNext: store.advance,
                    onReset: store.reset,
                    onExpand: store.expandWorkout
                )
            }
        case .raceGoal(let title, let subtitle, let daysUntil):
            RaceGoalCard(title: title, subtitle: subtitle, daysUntil: daysUntil)
        case .performance(let sessionCount, let trainedDays, let today):
            WeeklyPerformanceCard(sessionCount: sessionCount, trainedDays: trainedDays, today: today)
        case .simulations(let sims):
            SimulationRail(sims: sims)
        case .recentSessions(let sessions):
            RecentSessionsSection(
                sessions: sessions,
                onOpenDetail: { path.append(.sessionDetail($0)) },
                onSeeAll: { path.append(.history) }
            )
        case .unsupported:
            EmptyView()
        }
    }

    /// `horizontalArrangement = Arrangement.spacedBy(spacing.smd)` on the Compose grid.
    private static let compactColumns = [
        GridItem(.flexible(), spacing: Space.smd, alignment: .top),
        GridItem(.flexible(), spacing: Space.smd, alignment: .top),
    ]

    private var rows: [WidgetRow] {
        var result: [WidgetRow] = []
        for slot in store.slots {
            if slot.isCompact,
               let last = result.last,
               last.slots.count == 1,
               last.slots[0].isCompact {
                result[result.count - 1] = WidgetRow(slots: last.slots + [slot])
            } else {
                result.append(WidgetRow(slots: [slot]))
            }
        }
        return result
    }
}

private struct WidgetRow: Identifiable {
    let slots: [HomeSlot]
    var id: String { slots.map(\.id).joined(separator: "+") }
}

private extension HomeSlot {
    var isCompact: Bool {
        switch content {
        case .raceGoal, .performance: return true
        default: return false
        }
    }
}

// MARK: - Shared card chrome

/// The top-right radial glow both compact cards carry, pulsing 0.6 ⇄ 0.3 over 2.1s.
private struct AccentGlow: ViewModifier {
    @State private var bright = false

    func body(content: Content) -> some View {
        content.background(
            GeometryReader { geo in
                RadialGradient(
                    colors: [Obsidian.primary.opacity(bright ? 0.6 : 0.3), .clear],
                    center: .topTrailing,
                    startRadius: 0,
                    endRadius: max(geo.size.width, geo.size.height) * 0.7
                )
                .onAppear {
                    withAnimation(.easeInOut(duration: 2.1).repeatForever(autoreverses: true)) {
                        bright = true
                    }
                }
            }
        )
    }
}

// MARK: - Race goal

private struct RaceGoalCard: View {
    let title: String
    let subtitle: String
    let daysUntil: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text("RACE DAY")
                .labelMediumStyle()
                .foregroundStyle(Obsidian.primary)
            Text(title)
                .font(MSFont.titleSmall)
                .foregroundStyle(Obsidian.onSurface)
                .lineLimit(2)
            Text(subtitle)
                .font(MSFont.bodySmall)
                .foregroundStyle(Obsidian.onSurfaceVariant)
                .lineLimit(1)
            if let days = daysUntil {
                Spacer().frame(height: Space.sm)
                HStack(alignment: .firstTextBaseline, spacing: Space.sm) {
                    Text("\(days)")
                        .font(MSFont.displaySmall)
                        .foregroundStyle(Obsidian.onSurface)
                    Text(days == 1 ? "DAY" : "DAYS")
                        .labelMediumStyle()
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                }
            }
        }
        .padding(.horizontal, Space.smd)
        .padding(.vertical, Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(AccentGlow())
        .glassCard()
    }
}

// MARK: - Weekly performance

private struct WeeklyPerformanceCard: View {
    let sessionCount: Int
    let trainedDays: Set<Int64>
    let today: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text("This week".uppercased())
                .labelMediumStyle()
                .foregroundStyle(Obsidian.primary)
            Text(sessionCount == 1 ? "1 session" : "\(sessionCount) sessions")
                .font(MSFont.bodyMedium)
                .foregroundStyle(Obsidian.onSurface)
            Spacer().frame(height: Space.xs)

            // Monday-first week of 7. Android computes this in core/ui's buildCalendarWeeks, which is
            // an Android-only module, so the same UTC epoch-day arithmetic is repeated here.
            //
            // FlowLayout, not HStack — PerformanceCard.kt uses a FlowRow. Seven 30pt tiles cannot fit
            // a half-width grid cell on a phone, so the strip has to wrap onto two or three lines;
            // an HStack instead reports ~258pt as its minimum width and shoves the card out of its
            // column. Spacing matches the Compose FlowRow: smd across, sm down.
            FlowLayout(horizontalSpacing: Space.smd, verticalSpacing: Space.sm) {
                ForEach(weekCells, id: \.epochDay) { cell in
                    CalendarDayTile(cell: cell)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, Space.smd)
        .padding(.vertical, Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(AccentGlow())
        .glassCard()
    }

    /// Epoch day 0 is a Thursday, so Monday index within the week is `((day % 7) + 3) % 7` — the same
    /// expression `HomeViewModel.performanceLikeSlot` uses to find the current Monday.
    private var weekCells: [DayCell] { DayCell.week(today: today, trained: trainedDays) }
}

// MARK: - Simulations

/// A horizontal rail of photo tiles. Note `SimulationEntry.flag` is *badge text* ("Official Sim" /
/// "Half Sim"), not an emoji — the card art comes from the packaged photograph chosen by `type`.
private struct SimulationRail: View {
    let sims: [SimCard]

    var body: some View {
        VStack(alignment: .leading, spacing: Space.md) {
            SectionHeader(title: "Simulations")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.smd) {
                    ForEach(sims) { sim in
                        SimulationTile(sim: sim)
                    }
                }
                .padding(.trailing, Space.xs)
            }
        }
    }
}

private struct SimulationTile: View {
    let sim: SimCard

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Image(sim.isFullRace ? "template_full_hyrox" : "template_hyrox_sim")
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 264, height: 200)
                .clipped()

            // Bottom-anchored scrim so the text keeps contrast whatever the photograph does.
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: Obsidian.surfaceContainerLowest.opacity(0.4), location: 0.5),
                    .init(color: Obsidian.surfaceContainerLowest, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: Space.sm) {
                Text(sim.flag.uppercased())
                    .labelSmallStyle()
                    .foregroundStyle(Obsidian.onPrimary)
                    .padding(.horizontal, Space.sm)
                    .padding(.vertical, 2)
                    .background(Obsidian.primaryContainer, in: RoundedRectangle(cornerRadius: Radius.xs))

                VStack(alignment: .leading, spacing: Space.xs) {
                    Text(sim.title)
                        .font(MSFont.titleMedium)
                        .foregroundStyle(Obsidian.onSurface)
                        .lineLimit(2)
                    Text(sim.subtitle)
                        .font(MSFont.bodySmall)
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                        .lineLimit(1)
                }

                HStack(spacing: Space.sm) {
                    ForEach(sim.tags, id: \.self) { tag in
                        Text(tag.uppercased())
                            .labelSmallStyle()
                            .foregroundStyle(Obsidian.floatingTagText)
                            .padding(.horizontal, Space.sm)
                            .padding(.vertical, Space.xs)
                            .background(Obsidian.surfaceContainerHigh.opacity(0.8), in: Capsule())
                    }
                }
            }
            .padding(Space.md)
        }
        .frame(width: 264, height: 200)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }
}

// MARK: - Recent sessions

/// Not a single card: a section header plus one glass row per session, matching
/// `RecentSessionsCard.kt` + `core/ui/.../SessionRow.kt`.
private struct RecentSessionsSection: View {
    let sessions: [SessionRowModel]
    let onOpenDetail: (String) -> Void
    let onSeeAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.md) {
            SectionHeader(title: "Recent Sessions") {
                Button(action: onSeeAll) {
                    Text("See all")
                        .labelLargeStyle()
                        .foregroundStyle(Obsidian.primary)
                }
                .buttonStyle(.plain)
            }

            if sessions.isEmpty {
                HomePlaceholder(message: "No sessions yet. Start your first one.")
            } else {
                VStack(spacing: 0) {
                    ForEach(sessions) { session in
                        SessionRowView(session: session) { onOpenDetail(session.id) }
                    }
                }
            }
        }
    }
}

// MARK: - Live workout

private struct LiveWorkoutCard: View {
    let workout: ActiveWorkoutSnapshot
    let onTogglePause: () -> Void
    let onNext: () -> Void
    let onReset: () -> Void
    let onExpand: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            HStack {
                Text("LIVE")
                    .labelSmallStyle()
                    .foregroundStyle(Obsidian.onPrimary)
                    .padding(.horizontal, Space.sm)
                    .padding(.vertical, 2)
                    .background(Obsidian.primary, in: Capsule())
                Spacer()
                Text("Step \(workout.stepNumber) of \(workout.totalSteps)")
                    .labelMediumStyle()
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }

            Text(workout.title)
                .font(MSFont.titleMedium)
                .foregroundStyle(Obsidian.onSurface)

            // The stopwatch and split tracking are ActiveWorkoutController in commonMain — this only
            // renders them, and the clock formatter is shared too (core/domain/TimeText.kt).
            HStack(alignment: .firstTextBaseline, spacing: Space.md) {
                Text(TimeTextKt.formatClockSec(sec: workout.totalElapsedMs / 1000))
                    .font(MSFont.displaySmall.monospacedDigit())
                    .foregroundStyle(workout.paused ? Obsidian.onSurfaceVariant : Obsidian.onSurface)
                Text("split " + TimeTextKt.formatClockSec(sec: workout.splitElapsedMs / 1000))
                    .font(MSFont.bodySmall.monospacedDigit())
                    .foregroundStyle(workout.paused ? Obsidian.error : Obsidian.primary)
            }

            HStack(spacing: Space.sm) {
                PrimaryButton(title: workout.paused ? "Resume" : "Pause", action: onTogglePause)
                SecondaryButton(title: "Next", action: onNext)
                SecondaryButton(title: "Reset", action: onReset)
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard(radius: Radius.lg)
        .onTapGesture(perform: onExpand)
    }
}

private struct HomePlaceholder: View {
    let message: String

    var body: some View {
        Text(message)
            .font(MSFont.bodySmall)
            .foregroundStyle(Obsidian.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Space.md)
            .glassCard()
    }
}
