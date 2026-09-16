import Foundation
import Shared

/// Observable adapter around the shared `HistoryViewModel`.
///
/// Pattern B — the Kotlin state carries two `Set`s, two `Map`s and `Session`'s `Instant`s, all of
/// which box across the boundary. Converted once here so the view deals in Swift values.
///
/// **Free path only.** `HistoryViewModel` also exposes `pagedSessions: Flow<PagingData<Session>>`
/// for the Pro list, and Paging 3 has no Swift consumer at all. `uiState.freeRows` is a plain list of
/// the last 30 days and needs no bridge, so that is what iOS renders. What a Pro user loses on iOS
/// versus Android: the type filter, pagination past 30 days, and the PB pill. Everything else — the
/// streak card, the headers, the empty state, the row layout — is identical between tiers.
final class HistoryStore: ObservableObject {
    @Published private(set) var streakDays = 0
    @Published private(set) var longestStreakDays = 0
    @Published private(set) var days: [DayCell] = []
    @Published private(set) var rows: [SessionRowModel] = []

    private let viewModel: HistoryViewModel
    private var subscription: FlowSubscription?

    /// The calendar window History shows — `CalendarDays` in HistoryScreen.kt.
    private static let calendarDays = 30

    init() {
        viewModel = KoinIosKt.historyViewModel()
        if let state = viewModel.uiState.value as? HistoryUiState { apply(state) }
        subscription = FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
            guard let self, let state = value as? HistoryUiState else { return }
            self.apply(state)
        }
    }

    deinit {
        subscription?.cancel()
    }

    private func apply(_ state: HistoryUiState) {
        streakDays = Int(state.streakDays)
        longestStreakDays = Int(state.longestStreakDays)

        // A trailing 30 days ending today — `buildDayCells`, not Home's Monday-aligned week.
        days = DayCell.trailing(
            count: Self.calendarDays,
            today: state.todayEpochDay,
            trained: Self.unboxInt64Set(state.trainedEpochDays)
        )

        rows = state.freeRows.map { row in
            SessionRowModel(
                id: row.session.id,
                name: row.session.name,
                typeName: row.session.type.name,
                startedAtMillis: row.session.startedAt.toEpochMilliseconds(),
                durationSec: row.durationSec.map { Int(truncating: $0) }
                // isPb stays false: the PB pill is Pro-only on Android too.
            )
        }
    }

    private static func unboxInt64Set(_ value: Any?) -> Set<Int64> {
        guard let set = value as? NSSet else { return [] }
        return Set(set.compactMap { ($0 as? NSNumber)?.int64Value })
    }
}
