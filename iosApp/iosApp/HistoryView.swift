import SwiftUI
import Shared

/// History — SwiftUI counterpart of
/// `feature/history/src/androidMain/kotlin/com/mindset/HistoryScreen.kt`.
///
/// Not a tab: on Android `BottomNavTab` notes History "moved off the bar entirely — it is reached
/// from Home's 'See all'", so this is a push inside Home's navigation stack. It has a back button and
/// the wordmark rather than a "History" title — there is no such string anywhere on the screen.
struct HistoryView: View {
    let onOpenDetail: (String) -> Void
    var onOpenProfile: (() -> Void)?

    @StateObject private var store = HistoryStore()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StreakCalendarSection(
                    streakDays: store.streakDays,
                    longestStreakDays: store.longestStreakDays,
                    days: store.days
                )

                Spacer().frame(height: Space.lg)
                SectionHeader(title: "Recent")
                Spacer().frame(height: Space.md)

                if store.rows.isEmpty {
                    Text("No sessions logged yet.")
                        .font(MSFont.bodyMedium)
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                } else {
                    // Each row is its own glass surface — History, unlike Home, does not wrap them
                    // in a shared card.
                    ForEach(store.rows) { row in
                        SessionRowView(session: row) { onOpenDetail(row.id) }
                    }
                }
            }
            .padding(.horizontal, Space.md)
            .padding(.top, Space.sm)
            .padding(.bottom, Space.md)
        }
        .background(Obsidian.background)
        .mindSetToolbar(onOpenProfile: onOpenProfile)
    }
}

// MARK: - Streak + calendar

private struct StreakCalendarSection: View {
    let streakDays: Int
    let longestStreakDays: Int
    let days: [DayCell]

    var body: some View {
        VStack(alignment: .leading, spacing: Space.md) {
            SectionHeader(title: "Last 30 days") {
                Text(Self.monthLabel())
                    .labelMediumStyle()
                    .foregroundStyle(Obsidian.primary)
            }

            VStack(alignment: .leading, spacing: Space.sm) {
                HStack(alignment: .bottom) {
                    StreakStat(value: "\(streakDays)", label: "Day streak", showsFlame: true)
                    Spacer(minLength: Space.sm)
                    StreakStat(value: "\(longestStreakDays)", label: "Best", alignEnd: true)
                }
                .padding(.bottom, Space.xs)

                CalendarGrid(days: days)
            }
            .padding(Space.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassCard()
        }
    }

    private static func monthLabel() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: Date()).uppercased()
    }
}

private struct StreakStat: View {
    let value: String
    let label: String
    var showsFlame: Bool = false
    var alignEnd: Bool = false

    var body: some View {
        VStack(alignment: alignEnd ? .trailing : .leading, spacing: Space.xs) {
            // Label sits ABOVE the value here, unlike Profile's StatTile.
            Text(label.uppercased())
                .labelSmallStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
            HStack(spacing: Space.xs) {
                Text(value)
                    .font(MSFont.headlineMedium)
                    .foregroundStyle(Obsidian.onSurface)
                if showsFlame {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(Obsidian.primary)
                }
            }
        }
    }
}

/// Thirty tiles in rows of seven: four full rows and a short row of two.
///
/// The short row must left-align into the same columns. Compose builds this from weighted rows with
/// trailing spacers rather than a FlowRow, precisely because `SpaceBetween` spread the final row
/// across the full width and today drifted to the right edge. A fixed grid with trailing
/// placeholders reproduces that.
private struct CalendarGrid: View {
    let days: [DayCell]

    private static let columns = 7

    var body: some View {
        VStack(spacing: Space.sm) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 0) {
                    ForEach(row) { cell in
                        CalendarDayTile(cell: cell)
                            .frame(maxWidth: .infinity)
                    }
                    // Pad the short final row so its tiles keep the same column positions.
                    ForEach(0..<(Self.columns - row.count), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                    }
                }
            }
        }
    }

    private var rows: [[DayCell]] {
        stride(from: 0, to: days.count, by: Self.columns).map {
            Array(days[$0..<min($0 + Self.columns, days.count)])
        }
    }
}
