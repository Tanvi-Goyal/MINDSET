import Foundation
import Shared

/// Observable adapter around the shared `OnboardingViewModel`.
///
/// This is the store that makes the rest of the app work. `onComplete()` — running entirely in
/// `commonMain` — saves the athlete profile, creates the upcoming `RaceGoal` and writes the
/// division / gender / race-mode preferences in one go. Home's countdown card and the whole Stations
/// board are downstream of those writes: before onboarding runs, `StationsViewModel` short-circuits
/// to an empty board and `HomeViewModel.raceGoalSlot()` emits nil.
///
/// Pattern A (direct publish), like `StationsStore`: `OnboardingUiState` is flat — strings, booleans,
/// three optional enums and one boxed `Long?`. Nothing here needs the conversion layer `HomeStore` uses.
///
/// Note the flow is called `state`, not `uiState`, unlike every other ViewModel in the codebase.
final class OnboardingStore: ObservableObject {
    @Published private(set) var state: OnboardingUiState?

    private let viewModel: OnboardingViewModel
    private var subscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.onboardingViewModel()
        state = viewModel.state.value as? OnboardingUiState
        subscription = FlowObserverKt.subscribe(flow: viewModel.state) { [weak self] value in
            self?.state = value as? OnboardingUiState
        }
    }

    deinit {
        subscription?.cancel()
    }

    // MARK: Intents — thin pass-throughs; all validation and clamping lives in Kotlin.

    func setName(_ value: String) { viewModel.onFullName(value: value) }
    func setBodyweight(_ value: String) { viewModel.onBodyweight(value: value) }
    func setHeight(_ value: String) { viewModel.onHeight(value: value) }
    /// Steps by ±1, clamped 0...500 in Kotlin. Kotlin `Int` is 32-bit, hence `Int32`.
    func stepBodyweight(_ delta: Int32) { viewModel.stepBodyweight(delta: delta) }
    /// Clamped 0...108 in Kotlin.
    func stepHeight(_ delta: Int32) { viewModel.stepHeight(delta: delta) }
    func setRaceDate(_ millis: Int64?) {
        viewModel.onRaceDate(millis: millis.map { KotlinLong(longLong: $0) })
    }
    func setGender(_ gender: Gender) { viewModel.onGender(gender: gender) }
    func setTier(_ tier: Tier) { viewModel.onTier(tier: tier) }
    func setFormat(_ mode: RaceMode) { viewModel.onFormat(format: mode) }
    func setCity(_ value: String) { viewModel.onCity(value: value) }
    func next() { viewModel.onNext() }
    func back() { viewModel.onBack() }
    func complete() { viewModel.onComplete() }
}

/// Kotlin enum entries, by ordinal, without naming them in Swift.
///
/// Reading a selection uses `KotlinEnum.ordinal` directly; writing one indexes into `values()`.
/// Both directions therefore key on position, which is precisely what the Compose selector does.
///
/// Two reasons this indirection earns its place. First, a Kotlin `enum class` exports as an Obj-C
/// class whose entry spellings are generated — `Tier.OPEN` becomes something like `Tier.open`, and
/// `open` is a Swift declaration modifier, so hard-coding those names is a guess that only the
/// generated header can settle. Second, the Compose onboarding screen maps its hard-coded label
/// lists onto `ordinal` anyway, so going through `values()` reproduces Android exactly.
enum KotlinEnums {
    static func values<T: AnyObject>(_ array: KotlinArray<T>) -> [T] {
        (0..<array.size).compactMap { array.get(index: $0) }
    }

    /// Order matters and matches the Compose label lists: Women is index 0.
    static var genders: [Gender] { values(Gender.values()) }
    static var tiers: [Tier] { values(Tier.values()) }
    static var raceModes: [RaceMode] { values(RaceMode.values()) }
    /// Used by Profile's unit control. `WeightUnit { KG, LB }` — KG is index 0.
    static var weightUnits: [WeightUnit] { values(WeightUnit.values()) }
}
