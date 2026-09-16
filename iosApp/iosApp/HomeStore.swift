import Foundation
import Shared

// MARK: - Swift-native view models
//
// Everything below is a plain Swift type. Nothing in `HomeView` touches a Kotlin class, and that is
// deliberate — this file is an anti-corruption layer.
//
// `StationsStore` publishes the Kotlin `StationsUiState` straight through, because that state is
// flat pre-formatted strings. `HomeUiState` is the opposite: a sealed interface nested inside a
// sealed interface, a `Set<Long>`, two `Map`s, and a raw `Session` carrying `kotlin.time.Instant`.
// Passing that into SwiftUI would push three separate problems into every view:
//
//   * **No exhaustiveness.** Kotlin sealed types export as Objective-C protocols/classes, so Swift
//     can only `as?`-cast down them. Adding a 6th `Widget` would render a blank card at runtime
//     instead of failing the build.
//   * **Boxing.** `Set<Long>` arrives as `NSSet` of `KotlinLong`, `Map<String, Double>` as
//     `NSDictionary` of `KotlinDouble`. `set.contains(someInt64)` silently returns false.
//   * **Width.** Kotlin `Int` is 32-bit: it arrives as Swift `Int32`, not `Int`.
//
// Converting once, here, turns all three into Swift enums and value types — after which the compiler
// does its normal job. The cost is this mapping code; the benefit is that a shape change in Kotlin
// breaks compilation in one file instead of misrendering in five.

struct HomeSlot: Identifiable {
    let id: String
    let content: Content

    /// A real Swift enum — so `switch` in the view IS exhaustive, unlike a cast ladder.
    enum Content {
        case loading
        case failed(String)
        case liveWorkout
        case raceGoal(title: String, subtitle: String, daysUntil: Int?)
        case performance(sessionCount: Int, trainedDays: Set<Int64>, today: Int64)
        case simulations([SimCard])
        case recentSessions([SessionRowModel])
        /// Reached only if Kotlin gains a widget this mapping does not know about.
        case unsupported
    }
}

struct SimCard: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let flag: String
    let tags: [String]
    let isFullRace: Bool
}

struct ActiveWorkoutSnapshot {
    let title: String
    let stepNumber: Int
    let totalSteps: Int
    let totalElapsedMs: Int64
    let splitElapsedMs: Int64
    let paused: Bool
    let finished: Bool
}

// MARK: - Store

/// Observable adapter around the shared `HomeViewModel`.
///
/// Unlike `StationsStore` this bridges **two** flows from one ViewModel — `uiState` and
/// `activeWorkout` — which means two independent `FlowSubscription`s to cancel. `activeWorkout` is
/// the live Hyrox race timer, driven entirely by `ActiveWorkoutController` in
/// `core/data/src/commonMain/`: the stopwatch, the split tracking and the step advance are shared
/// Kotlin, and both platforms only render it.
final class HomeStore: ObservableObject {
    @Published private(set) var slots: [HomeSlot] = []
    @Published private(set) var activeWorkout: ActiveWorkoutSnapshot?

    private let viewModel: HomeViewModel
    private var stateSubscription: FlowSubscription?
    private var workoutSubscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.homeViewModel()

        // Seed synchronously from `.value` — `subscribe` does not replay on collect, so without
        // this the first rendered frame is empty even when state already exists.
        if let initial = viewModel.uiState.value as? HomeUiState {
            slots = Self.map(initial)
        }
        if let initial = viewModel.activeWorkout.value as? ActiveWorkout {
            activeWorkout = Self.map(initial)
        }

        stateSubscription = FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
            guard let state = value as? HomeUiState else { return }
            self?.slots = Self.map(state)
        }
        // Nullable flow: `nil` is a real emission here (no workout running), so this deliberately
        // does not `guard` the cast — it assigns nil through.
        workoutSubscription = FlowObserverKt.subscribe(flow: viewModel.activeWorkout) { [weak self] value in
            self?.activeWorkout = (value as? ActiveWorkout).map(Self.map)
        }
    }

    // MARK: Intents

    func expandWorkout() { viewModel.onExpandWorkout() }
    func togglePause() { viewModel.onToggleWorkoutPause() }
    func advance() { viewModel.onAdvanceWorkout() }
    func reset() { viewModel.onResetWorkout() }

    deinit {
        stateSubscription?.cancel()
        workoutSubscription?.cancel()
    }

    // MARK: - Kotlin -> Swift mapping

    private static func map(_ state: HomeUiState) -> [HomeSlot] {
        state.widgets.map { slot in
            // `WidgetType` is a Kotlin enum, so it is a class here, not a Swift enum. Its `name`
            // (the constant name) is a stable unique String — good enough as a SwiftUI list id.
            HomeSlot(id: slot.type.name, content: content(of: slot.state))
        }
    }

    private static func content(of state: WidgetState) -> HomeSlot.Content {
        // The cast ladder lives here and nowhere else. Two levels, because `WidgetState.Content`
        // wraps a second sealed hierarchy.
        //
        // Objective-C flattens nested Kotlin types, so `WidgetState.Content` is spelled
        // `WidgetStateContent` and `Widget.RaceGoalWidget` is `WidgetRaceGoalWidget`. If any of
        // these names fail to compile, read the generated header for the real spelling:
        //   shared/build/.../Shared.framework/Headers/Shared.h
        if state is WidgetStateLoading { return .loading }
        if let failure = state as? WidgetStateError { return .failed(failure.message) }
        guard let wrapped = state as? WidgetStateContent else { return .unsupported }

        let widget = wrapped.widget

        if widget is WidgetLiveWorkoutWidget {
            return .liveWorkout
        }
        if let race = widget as? WidgetRaceGoalWidget {
            // Kotlin `Int?` boxes to `KotlinInt?`; unwrap to a native Swift Int.
            return .raceGoal(
                title: race.title,
                subtitle: race.subtitle,
                daysUntil: race.daysUntil.map { Int(truncating: $0) }
            )
        }
        if let perf = widget as? WidgetPerformanceWidget {
            return .performance(
                // Kotlin Int is 32-bit -> Swift Int32.
                sessionCount: Int(perf.sessionCount),
                trainedDays: unboxInt64Set(perf.trainedEpochDays),
                today: perf.todayEpochDay
            )
        }
        if let sim = widget as? WidgetSimulationWidget {
            return .simulations(sim.sims.map { entry in
                SimCard(
                    id: entry.id,
                    title: entry.title,
                    subtitle: entry.subtitle,
                    flag: entry.flag,
                    tags: entry.tags,
                    isFullRace: entry.type.name == "FULL_HYROX"
                )
            })
        }
        if let recent = widget as? WidgetRecentSessionsWidget {
            let volumes = unboxDoubleMap(recent.volumesById)
            let durations = unboxIntMap(recent.durationsById)
            // `volumes` is read but never rendered — SessionRow ignores it on Android too.
            _ = volumes
            return .recentSessions(recent.sessions.map { session in
                SessionRowModel(
                    id: session.id,
                    name: session.name,
                    typeName: session.type.name,
                    // `Session` leaks the domain type, whose timestamps are the *stdlib*
                    // `kotlin.time.Instant` (not kotlinx-datetime). It bridges as an opaque class,
                    // NOT a Foundation `Date` — so convert explicitly at the seam.
                    startedAtMillis: session.startedAt.toEpochMilliseconds(),
                    durationSec: durations[session.id]
                )
            })
        }
        return .unsupported
    }

    private static func map(_ workout: ActiveWorkout) -> ActiveWorkoutSnapshot {
        ActiveWorkoutSnapshot(
            title: workout.current?.title ?? "Workout",
            stepNumber: Int(workout.currentIndex) + 1,
            totalSteps: Int(workout.totalSteps),
            totalElapsedMs: workout.totalElapsedMs,
            splitElapsedMs: workout.splitElapsedMs,
            paused: workout.paused,
            finished: workout.finished
        )
    }

    // MARK: Unboxing helpers
    //
    // Routed through `NSSet`/`NSDictionary` rather than the bridged generic Swift types on purpose:
    // exactly how Kotlin collection generics surface depends on the generated header, but the
    // Foundation cast is always valid, so these keep compiling if that spelling shifts.
    // Taking `Any?` rather than a bridged generic type is the same defence at the parameter.

    private static func unboxInt64Set(_ value: Any?) -> Set<Int64> {
        guard let set = value as? NSSet else { return [] }
        return Set(set.compactMap { ($0 as? NSNumber)?.int64Value })
    }

    private static func unboxDoubleMap(_ value: Any?) -> [String: Double] {
        guard let map = value as? NSDictionary else { return [:] }
        return map.reduce(into: [String: Double]()) { result, entry in
            guard let key = entry.key as? String, let boxed = entry.value as? NSNumber else { return }
            result[key] = boxed.doubleValue
        }
    }

    private static func unboxIntMap(_ value: Any?) -> [String: Int] {
        guard let map = value as? NSDictionary else { return [:] }
        return map.reduce(into: [String: Int]()) { result, entry in
            guard let key = entry.key as? String, let boxed = entry.value as? NSNumber else { return }
            result[key] = boxed.intValue
        }
    }
}
