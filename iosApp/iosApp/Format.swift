import Foundation
import Shared

/// Display formatters. Weights are stored and computed in kg; conversion to the user's `WeightUnit`
/// reuses the shared `Units` object so the maths lives once, in Kotlin.
///
/// The date and duration helpers below are **reimplemented** rather than shared, because their
/// Android counterparts (`relativeDate` / `formatDuration` in `core/ui/.../SessionRow.kt`) live in an
/// Android-only module. The genuinely shared clock formatter — `formatClockSec` in
/// `core/domain/TimeText.kt` — is called directly as `TimeTextKt.formatClockSec(sec:)`.
enum Format {
    /// Compact volume in the user's unit, e.g. "12.4k lb".
    static func volume(_ kg: Double, _ unit: WeightUnit) -> String {
        let v = Units.shared.toDisplay(kg: kg, unit: unit)
        let label = Units.shared.label(unit: unit)
        return v >= 1000 ? String(format: "%.1fk %@", v / 1000, label) : String(format: "%.0f %@", v, label)
    }

    /// Plain weight for a single load, e.g. "40 kg" / "88 lb".
    static func weight(_ kg: Double, _ unit: WeightUnit) -> String {
        let v = Units.shared.toDisplay(kg: kg, unit: unit)
        return String(format: "%.0f %@", v, Units.shared.label(unit: unit))
    }

    /// "Today" / "Yesterday" / "Sun, 12 Jul" — matching SessionRow.kt's `relativeDate`.
    static func relativeDay(_ epochMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000)
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, d MMM"
        return formatter.string(from: date)
    }

    /// "1h 25m" past an hour, else "25m 10s" — matching SessionRow.kt's `formatDuration`.
    static func duration(_ totalSeconds: Int) -> String {
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m \(seconds)s"
    }
}
