import Foundation
import Shared

/// Observable adapter around the shared `SessionDetailViewModel`.
///
/// Pure projection — the ViewModel has no intents at all, only `uiState`. It is resolved with the
/// one parameterised Koin accessor in use: `sessionDetailViewModel(sessionId:)`.
///
/// This store keeps hold of the Kotlin `LoggedItemUi` values rather than flattening them completely,
/// because set lines are formatted by the **shared** `SetEntry.detailSummary(capture:unit:)` — so
/// each set and its `captureFields` have to survive to the view. That is the whole trick for this
/// screen: the `when` over the six-case sealed `CaptureFields` runs inside Kotlin, and Swift passes
/// the value straight back without ever inspecting it.
final class SessionDetailStore: ObservableObject {
    @Published private(set) var name = ""
    @Published private(set) var typeName = ""
    @Published private(set) var startedAtMillis: Int64 = 0
    /// Nil when nothing in the session was timed — renders as an em dash, not "00:00".
    @Published private(set) var totalTimeMs: Int64?
    @Published private(set) var totalVolumeKg: Double = 0
    @Published private(set) var items: [LoggedItemUi] = []

    private let viewModel: SessionDetailViewModel
    private var subscription: FlowSubscription?

    init(sessionId: String) {
        viewModel = KoinIosKt.sessionDetailViewModel(sessionId: sessionId)
        if let state = viewModel.uiState.value as? SessionDetailUiState { apply(state) }
        subscription = FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
            guard let self, let state = value as? SessionDetailUiState else { return }
            self.apply(state)
        }
    }

    deinit {
        subscription?.cancel()
    }

    /// True when any item carries a segment key — Hyrox sessions render as a race timeline, plain
    /// sessions as exercise cards.
    var hasTimeline: Bool { items.contains { $0.segmentKey != nil } }

    /// 1-based race position per item, nil for runs and for non-station items.
    var stationNumbers: [String: Int] {
        var result: [String: Int] = [:]
        var number = 0
        for item in items {
            guard let key = item.segmentKey, !key.contains("run") else { continue }
            number += 1
            result[item.loggedItemId] = number
        }
        return result
    }

    private func apply(_ state: SessionDetailUiState) {
        name = state.name
        typeName = state.type
        startedAtMillis = state.startedAt
        totalTimeMs = state.totalTimeMs.map { Int64(truncating: $0) }
        totalVolumeKg = state.totalVolumeKg
        items = state.items
    }
}
