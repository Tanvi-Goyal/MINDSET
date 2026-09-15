import Foundation
import Shared

// MARK: - Swift-native models
//
// Pattern B, like HomeStore. The Kotlin state here is three levels deep
// (LogSectionUi -> LoggedItemUi -> SetEntry) and every SetEntry carries twelve boxed nullables plus
// three `kotlin.time.Instant` timestamps that ride along on every emission. Flattening once at the
// boundary means the views deal in plain Swift values and a Kotlin shape change breaks one file.
//
// The flattening mirrors the Compose screen exactly: section labels are never drawn (toLogSections()
// hardcodes them to "Block"), so both platforms render one card per logged item using sets.first().

struct StationCard: Identifiable {
    /// `loggedItemId` — what `removeEntry` takes. Note this is NOT the set id.
    let id: String
    /// `SetEntry.id` — what every capture intent and `confirmStation` take.
    let setId: String
    let name: String
    let segmentKey: String?
    /// Race position, or nil for runs.
    let stationNumber: Int?
    /// A time has been committed to the database.
    let isConfirmed: Bool
    /// Which inputs to show. Derived from the set's *target* columns, exactly as
    /// LogWorkoutScreen.kt does — `captureFields` is not consulted by this screen on either platform.
    let showsReps: Bool
    let showsLoad: Bool
    let showsDist: Bool
    let standard: String?
}

struct DraftValues {
    /// Raw digit buffer, max 6. "305" means 3:05.
    var timeDigits: String = ""
    var reps: String = ""
    /// In the user's display unit, not kg — the ViewModel converts on commit.
    var load: String = ""
    var dist: String = ""
}

struct StationChoice: Identifiable {
    var id: String { segmentKey }
    let segmentKey: String
    let name: String
    let standard: String
}

struct LogHeader {
    let sessionName: String
    /// SessionType constant name, carried as a String so the view never names an Obj-C enum entry.
    let typeName: String
    let startedAtMillis: Int64
    let notes: String
}

// MARK: - Tab store

/// Resolves *which* session the Log tab is editing.
///
/// `LogTabViewModel.init` launches `resumeOrCreateSession`, so `sessionId` is nil for the first frame
/// and again transiently after `onCompleted()`. That nil is overloaded — it means both "still
/// resolving" and "just finished, re-resolving" — and there is no error channel, so if the DB call
/// throws it stays nil forever. Android papers over the same gap with a placeholder that looks like
/// chrome; the iOS view does the same.
final class LogTabStore: ObservableObject {
    @Published private(set) var sessionId: String?

    private let viewModel: LogTabViewModel
    private var subscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.logTabViewModel()
        sessionId = viewModel.sessionId.value as? String
        subscription = FlowObserverKt.subscribe(flow: viewModel.sessionId) { [weak self] value in
            self?.sessionId = value as? String
        }
    }

    /// Clears the finished session and resolves a fresh one.
    func completed() { viewModel.onCompleted() }

    deinit {
        subscription?.cancel()
    }
}

// MARK: - Workout store

/// Owns one `LogWorkoutViewModel`, keyed to a session id. Rebuilt when that id changes — Android gets
/// this for free from `koinViewModel(key = sessionId)`; on iOS the store has to be replaced by hand.
///
/// Four subscriptions, all cancelled in `deinit`. Nothing calls `onCleared()` on iOS, and this
/// ViewModel holds a 400ms notes-debounce collector alongside its `WhileSubscribed(5_000)` upstreams,
/// so leaking them leaks live coroutines for the life of the process.
final class LogWorkoutStore: ObservableObject {
    @Published private(set) var header: LogHeader?
    @Published private(set) var cards: [StationCard] = []
    @Published private(set) var choices: [StationChoice] = []
    @Published private(set) var drafts: [String: DraftValues] = [:]

    /// `close`/`finish` share a private `exiting` flag in Kotlin: once either runs the ViewModel is
    /// spent, and a second call returns *without* invoking the callback. There is no completion
    /// StateFlow to observe, so the guard has to be mirrored here or a double-tap silently hangs.
    private(set) var isFinishing = false

    private let viewModel: LogWorkoutViewModel
    private var subscriptions: [FlowSubscription] = []
    private var standards: [String: String] = [:]

    init(sessionId: String) {
        viewModel = KoinIosKt.logWorkoutViewModel(sessionId: sessionId)

        standards = Self.stringMap(viewModel.stationStandards.value)
        if let state = viewModel.uiState.value as? LogWorkoutUiState { apply(state) }
        choices = Self.choices(viewModel.stations.value)
        drafts = Self.draftMap(viewModel.drafts.value)

        subscriptions = [
            FlowObserverKt.subscribe(flow: viewModel.stationStandards) { [weak self] value in
                guard let self else { return }
                self.standards = Self.stringMap(value)
                // Re-derive the cards: `standard` is looked up from this map.
                if let state = self.viewModel.uiState.value as? LogWorkoutUiState { self.apply(state) }
            },
            FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
                guard let self, let state = value as? LogWorkoutUiState else { return }
                self.apply(state)
            },
            FlowObserverKt.subscribe(flow: viewModel.stations) { [weak self] value in
                self?.choices = Self.choices(value)
            },
            FlowObserverKt.subscribe(flow: viewModel.drafts) { [weak self] value in
                self?.drafts = Self.draftMap(value)
            },
        ]
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
    }

    // MARK: Intents

    func setTime(_ setId: String, _ digits: String) { viewModel.onTimeChange(setId: setId, digits: digits) }
    func setReps(_ setId: String, _ value: String) { viewModel.onRepsChange(setId: setId, value: value) }
    func setLoad(_ setId: String, _ value: String) { viewModel.onLoadChange(setId: setId, value: value) }
    func setDist(_ setId: String, _ value: String) { viewModel.onDistChange(setId: setId, value: value) }
    func setNotes(_ notes: String) { viewModel.onNotesChange(notes: notes) }
    func confirm(_ setId: String) { viewModel.confirmStation(setId: setId) }
    /// Takes the logged-item id, not the set id.
    func remove(_ entryId: String) { viewModel.removeEntry(entryId: entryId) }
    func addStation(_ segmentKey: String) { viewModel.addStation(segmentKey: segmentKey) }

    /// Both arguments are always passed: the Kotlin default on `replaceExisting` generates no Swift
    /// overload. `replaceExisting: true` soft-deletes every entry in the session, with no undo.
    func addVariant(_ variant: HyroxVariant, replaceExisting: Bool) {
        viewModel.addVariant(variant: variant, replaceExisting: replaceExisting)
    }

    /// Commits every draft, finishes the session, then calls back on the main actor. Single-shot.
    func finish(onDone: @escaping () -> Void) {
        guard !isFinishing else { return }
        isFinishing = true
        // The callback comes back from viewModelScope; hop to the main actor before touching any
        // SwiftUI state. Nothing captures `self` — the store is deliberately discarded after this.
        viewModel.finish {
            Task { @MainActor in onDone() }
        }
    }

    // MARK: Kotlin -> Swift mapping

    private func apply(_ state: LogWorkoutUiState) {
        header = LogHeader(
            sessionName: state.sessionName,
            typeName: state.sessionType.name,
            startedAtMillis: state.startedAtMillis,
            notes: state.notes
        )
        cards = Self.flatten(state.sections, standards: standards)
    }

    /// Flattens sections -> items -> first set, and numbers the stations.
    ///
    /// Numbering mirrors LogWorkoutScreen.kt: increment only for a non-run segment, so runs carry no
    /// "STATION n" badge. Items with no segment key or no sets are skipped, as on Android.
    private static func flatten(_ sections: [LogSectionUi], standards: [String: String]) -> [StationCard] {
        var result: [StationCard] = []
        var number = 0

        for section in sections {
            for item in section.items {
                guard let segmentKey = item.segmentKey, let set = item.sets.first else { continue }

                let isRun = segmentKey.contains("run")
                if !isRun { number += 1 }

                let targetReps = item.sets.first?.targetReps
                result.append(
                    StationCard(
                        id: item.loggedItemId,
                        setId: set.id,
                        name: item.exerciseName,
                        segmentKey: segmentKey,
                        stationNumber: isRun ? nil : number,
                        isConfirmed: set.timeSec != nil,
                        showsReps: targetReps != nil,
                        showsLoad: targetReps == nil && set.targetLoadKg != nil,
                        showsDist: set.targetDistanceM != nil,
                        standard: standards[segmentKey]
                    )
                )
            }
        }
        return result
    }

    private static func choices(_ value: Any?) -> [StationChoice] {
        guard let options = value as? [StationOption] else { return [] }
        return options.map {
            StationChoice(segmentKey: $0.segmentKey, name: $0.name, standard: $0.standard)
        }
    }

    // Routed through NSDictionary rather than a bridged generic type, for the same reason as
    // HomeStore's unboxing helpers: the Foundation cast stays valid whatever the header spells.
    private static func stringMap(_ value: Any?) -> [String: String] {
        guard let map = value as? NSDictionary else { return [:] }
        return map.reduce(into: [String: String]()) { result, entry in
            guard let key = entry.key as? String, let text = entry.value as? String else { return }
            result[key] = text
        }
    }

    private static func draftMap(_ value: Any?) -> [String: DraftValues] {
        guard let map = value as? NSDictionary else { return [:] }
        return map.reduce(into: [String: DraftValues]()) { result, entry in
            guard let key = entry.key as? String, let draft = entry.value as? StationDraft else { return }
            result[key] = DraftValues(
                timeDigits: draft.timeDigits,
                reps: draft.reps,
                load: draft.load,
                dist: draft.dist
            )
        }
    }
}

// MARK: - Variants

/// `HyroxVariant { FULL, FIRST_HALF, SECOND_HALF, HALVED }`. The chip labels live in the Compose
/// screen, not commonMain, so they are restated here and keyed by ordinal like every other enum.
enum RaceVariant {
    static var all: [HyroxVariant] { KotlinEnums.values(HyroxVariant.values()) }

    static func label(_ variant: HyroxVariant) -> String {
        switch variant.name {
        case "FULL": return "Full"
        case "FIRST_HALF": return "1st Half"
        case "SECOND_HALF": return "2nd Half"
        case "HALVED": return "Halved"
        default: return variant.name.capitalized
        }
    }
}
