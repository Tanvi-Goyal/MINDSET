import SwiftUI
import Shared

/// App-wide preferences, resolved once at the root and injected via `.environmentObject`. Observes
/// the shared `PreferencesViewModel` (same Flow bridge as every other screen) and forwards its intents.
///
/// No `colorScheme` here any more, deliberately. `MindSetTheme`
/// (`core/designsystem/src/main/kotlin/com/mindset/Theme.kt`) always applies `MindSetColorScheme`,
/// which is a `darkColorScheme(...)` — the app is dark-only on Android and `ThemeMode` changes
/// nothing there. Mapping it to `preferredColorScheme` on iOS was what made Profile render in system
/// light chrome. The root now forces `.dark`, matching Android.
final class PreferencesStore: ObservableObject {
    @Published private(set) var units: WeightUnit = .kg
    /// `nil` until the first emission, so a returning user never sees onboarding flash by while
    /// storage is still being read. Android covers the same gap with its splash destination.
    @Published private(set) var isOnboardingComplete: Bool?

    private let viewModel: PreferencesViewModel
    private var subscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.preferencesViewModel()
        if let initial = viewModel.preferences.value as? UserPreferences {
            apply(initial)
        }
        subscription = FlowObserverKt.subscribe(flow: viewModel.preferences) { [weak self] value in
            guard let prefs = value as? UserPreferences else { return }
            self?.apply(prefs)
        }
    }

    private func apply(_ prefs: UserPreferences) {
        units = prefs.weightUnit
        isOnboardingComplete = prefs.isOnboardingComplete
    }

    func setUnit(_ unit: WeightUnit) { viewModel.onWeightUnitChange(unit: unit) }

    deinit {
        subscription?.cancel()
    }
}
