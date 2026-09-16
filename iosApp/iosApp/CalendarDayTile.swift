import SwiftUI

/// One day in a training calendar — `CalendarDayDot` from `core/ui/.../CalendarDayDot.kt`.
///
/// Shared by Home's week strip and History's 30-day grid. Both screens get their epoch days from the
/// same Kotlin state (`trainedEpochDays: Set<Long>` + `todayEpochDay`), but they **window it
/// differently**, so the two builders below are deliberately separate:
///
/// - Home uses `buildCalendarWeeks(..., weeks: 1)` — a Monday-aligned week of seven.
/// - History uses `buildDayCells(..., count: 30)` — a trailing thirty days ending on today, not
///   aligned to anything.
///
/// Those builders live in `core/ui` on Android, which is an Android-only module, so the arithmetic is
/// repeated here. It is UTC on both sides, matching `WeekCalendar.kt`.
struct DayCell: Identifiable {
    enum State { case today, trained, idle }

    let epochDay: Int64
    let state: State

    var id: Int64 { epochDay }

    /// Day-of-month in UTC, as on the Kotlin side.
    var dayOfMonth: Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
        let date = Date(timeIntervalSince1970: Double(epochDay) * 86_400)
        return calendar.component(.day, from: date)
    }

    /// `cellFor` in WeekCalendar.kt — TODAY wins over TRAINED, so today never shows the check badge.
    static func cell(day: Int64, today: Int64, trained: Set<Int64>) -> DayCell {
        let state: State = day == today ? .today : (trained.contains(day) ? .trained : .idle)
        return DayCell(epochDay: day, state: state)
    }

    /// Monday-aligned week of seven containing `today`.
    ///
    /// Epoch day 0 is a Thursday, so the Monday index within the week is `((day % 7) + 3) % 7` — the
    /// same expression `HomeViewModel.performanceLikeSlot` uses to find the current Monday.
    static func week(today: Int64, trained: Set<Int64>) -> [DayCell] {
        let monday = today - (((today % 7) + 3) % 7)
        return (0..<7).map { cell(day: monday + Int64($0), today: today, trained: trained) }
    }

    /// Trailing `count` days ending on today — `buildDayCells` in WeekCalendar.kt.
    static func trailing(count: Int, today: Int64, trained: Set<Int64>) -> [DayCell] {
        stride(from: Int64(count - 1), through: 0, by: -1)
            .map { cell(day: today - $0, today: today, trained: trained) }
    }
}

/// A 30pt rounded square, not a dot, with a check badge overlapping the top-right corner on trained
/// days.
struct CalendarDayTile: View {
    let cell: DayCell

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Text("\(cell.dayOfMonth)")
                .labelLargeStyle()
                .fontWeight(cell.state == .today ? .bold : .regular)
                .foregroundStyle(foreground)
                .frame(width: 30, height: 30)
                .background(background, in: RoundedRectangle(cornerRadius: Radius.sm))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .strokeBorder(cell.state == .trained ? Obsidian.primary : .clear, lineWidth: 1)
                )

            if cell.state == .trained {
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(Obsidian.onPrimary)
                    .frame(width: 16, height: 16)
                    .background(Obsidian.primary, in: Circle())
                    .offset(x: 4, y: -4)
            }
        }
        .frame(width: 30, height: 30)
    }

    private var background: Color {
        switch cell.state {
        case .today: return Obsidian.primary
        case .trained: return Obsidian.primary.opacity(0.18)
        case .idle: return Obsidian.glassFill
        }
    }

    private var foreground: Color {
        switch cell.state {
        case .today: return Obsidian.onPrimary
        case .trained: return Obsidian.onSurface
        case .idle: return Obsidian.onSurfaceVariant
        }
    }
}
