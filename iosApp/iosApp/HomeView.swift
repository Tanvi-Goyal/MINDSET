import SwiftUI
import Shared

/// Home — an ordered list of widgets, rendered from `HomeStore.slots`.
///
/// SwiftUI counterpart of `feature/home/src/androidMain/kotlin/com/mindset/HomeScreen.kt`. The
/// Android version renders a 2-column `LazyVerticalGrid` where Race Goal and Performance take one
/// cell each and every other widget spans the full width. SwiftUI's `LazyVGrid` has no per-item
/// span, so the same layout is expressed by grouping consecutive compact widgets into an `HStack`.
///
/// Note the `switch` below is exhaustive with no `default`. That is only possible because
/// `HomeStore` already converted Kotlin's nested sealed interfaces into a Swift enum — see the long
/// comment at the top of HomeStore.swift for why that conversion earns its keep.
struct HomeView: View {
    @StateObject private var store = HomeStore()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.lg) {
                    ForEach(rows, id: \.id) { row in
                        if row.slots.count == 1, let slot = row.slots.first {
                            widget(for: slot)
                        } else {
                            HStack(alignment: .top, spacing: Space.smd) {
                                ForEach(row.slots) { slot in
                                    widget(for: slot)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, Space.md)
                .padding(.vertical, Space.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Obsidian.background)
            .navigationTitle("MindSet")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Obsidian.background, for: .navigationBar)
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
            PerformanceCard(sessionCount: sessionCount, trainedDays: trainedDays, today: today)
        case .simulations(let sims):
            SimulationSection(sims: sims)
        case .recentSessions(let sessions):
            RecentSessionsCard(sessions: sessions)
        case .unsupported:
            EmptyView()
        }
    }

    /// Groups consecutive compact widgets into shared rows, preserving order.
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

// MARK: - Cards

private struct RaceGoalCard: View {
    let title: String
    let subtitle: String
    let daysUntil: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            Label("RACE", systemImage: "flag.checkered")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Obsidian.primary)
            Text(title)
                .font(.headline)
                .foregroundStyle(Obsidian.onSurface)
                .lineLimit(2)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(Obsidian.onSurfaceVariant)
                .lineLimit(2)
            if let days = daysUntil {
                HStack(alignment: .firstTextBaseline, spacing: Space.xs) {
                    Text("\(days)")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(Obsidian.primary)
                    Text(days == 1 ? "day out" : "days out")
                        .font(.caption)
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                }
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}

private struct PerformanceCard: View {
    let sessionCount: Int
    let trainedDays: Set<Int64>
    let today: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            Text("THIS WEEK")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Obsidian.primary)
            Text("\(sessionCount)")
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(Obsidian.onSurface)
            Text(sessionCount == 1 ? "session" : "sessions")
                .font(.caption)
                .foregroundStyle(Obsidian.onSurfaceVariant)

            // The last 7 days, oldest first. `trainedDays` is an epoch-day Set that was unboxed
            // from Kotlin's `Set<Long>` in HomeStore — comparing raw Int64s here works precisely
            // because that unboxing happened.
            HStack(spacing: Space.xs) {
                ForEach(weekDays, id: \.self) { day in
                    Circle()
                        .fill(trainedDays.contains(day) ? Obsidian.primary : Obsidian.glassBorder)
                        .frame(width: 8, height: 8)
                }
            }
            .padding(.top, Space.xs)
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }

    private var weekDays: [Int64] { (0..<7).map { today - Int64(6 - $0) } }
}

private struct SimulationSection: View {
    let sims: [SimCard]

    var body: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            Text("RACE SIMULATIONS")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Obsidian.primary)
            ForEach(sims) { sim in
                HStack(alignment: .center, spacing: Space.smd) {
                    Text(sim.flag).font(.title2)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(sim.title)
                            .font(.subheadline.bold())
                            .foregroundStyle(Obsidian.onSurface)
                        Text(sim.subtitle)
                            .font(.caption)
                            .foregroundStyle(Obsidian.onSurfaceVariant)
                        if !sim.tags.isEmpty {
                            Text(sim.tags.joined(separator: " • "))
                                .font(.caption2)
                                .foregroundStyle(Obsidian.outline)
                        }
                    }
                    Spacer(minLength: 0)
                    Text(sim.isFullRace ? "FULL" : "HALF")
                        .font(.caption2.bold())
                        .foregroundStyle(Obsidian.coral)
                }
                .padding(Space.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassCard()
            }
        }
    }
}

private struct RecentSessionsCard: View {
    let sessions: [RecentSession]

    var body: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            Text("RECENT SESSIONS")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Obsidian.primary)

            if sessions.isEmpty {
                Text("No sessions logged yet.")
                    .font(.footnote)
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            } else {
                ForEach(sessions) { session in
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(session.name)
                                .font(.subheadline.bold())
                                .foregroundStyle(Obsidian.onSurface)
                            Text(Format.relativeDate(session.startedAtMillis))
                                .font(.caption2)
                                .foregroundStyle(Obsidian.onSurfaceVariant)
                        }
                        Spacer(minLength: Space.sm)
                        VStack(alignment: .trailing, spacing: 2) {
                            if let seconds = session.durationSec {
                                // Reuses the SHARED formatter (core/domain/TimeText.kt) rather than
                                // reimplementing mm:ss in Swift — same output as Android, by construction.
                                Text(TimeTextKt.formatClockSec(sec: Int64(seconds)))
                                    .font(.footnote.bold().monospacedDigit())
                                    .foregroundStyle(Obsidian.onSurface)
                            }
                            if session.volumeKg > 0 {
                                Text(Format.weight(session.volumeKg, .kg))
                                    .font(.caption2)
                                    .foregroundStyle(Obsidian.outline)
                            }
                        }
                    }
                    .padding(.vertical, Space.sm)
                    if session.id != sessions.last?.id {
                        Rectangle().fill(Obsidian.glassBorder).frame(height: 1)
                    }
                }
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}

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
                    .font(.caption2.bold())
                    .foregroundStyle(Obsidian.onPrimary)
                    .padding(.horizontal, Space.sm)
                    .padding(.vertical, 2)
                    .background(Obsidian.primary, in: Capsule())
                Spacer()
                Text("Step \(workout.stepNumber) of \(workout.totalSteps)")
                    .font(.caption)
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }

            Text(workout.title)
                .font(.headline)
                .foregroundStyle(Obsidian.onSurface)

            // Total and split are both driven by ActiveWorkoutController in commonMain — the
            // stopwatch itself is shared Kotlin; this view only renders it.
            HStack(alignment: .firstTextBaseline, spacing: Space.md) {
                Text(TimeTextKt.formatClockSec(sec: workout.totalElapsedMs / 1000))
                    .font(.system(size: 32, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(Obsidian.onSurface)
                Text("split " + TimeTextKt.formatClockSec(sec: workout.splitElapsedMs / 1000))
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(Obsidian.onSurfaceVariant)
            }

            HStack(spacing: Space.sm) {
                Button(action: onTogglePause) {
                    Label(workout.paused ? "Resume" : "Pause",
                          systemImage: workout.paused ? "play.fill" : "pause.fill")
                        .font(.footnote.bold())
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Obsidian.primary)

                Button(action: onNext) {
                    Label("Next", systemImage: "forward.fill")
                        .font(.footnote.bold())
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(Obsidian.onSurfaceVariant)

                Button(action: onReset) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.footnote.bold())
                }
                .buttonStyle(.bordered)
                .tint(Obsidian.outline)
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
            .font(.footnote)
            .foregroundStyle(Obsidian.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Space.md)
            .glassCard()
    }
}
