import Foundation
import Shared

/// Observable adapter around the shared `StationsViewModel`.
///
/// This is the whole KMP→SwiftUI seam, and it is worth reading line by line, because on Android the
/// equivalent is a single call: `viewModel.uiState.collectAsStateWithLifecycle()`. iOS has no such
/// thing, so the four jobs that one Compose function does are done by hand here:
///
///  1. **Resolve** the ViewModel. Android uses `koinViewModel()`; Swift cannot call Koin's reified
///     `get<T>()` (Kotlin generics do not survive the Objective-C boundary), so `KoinIos.kt` exposes
///     one concrete top-level function per ViewModel and Swift calls `KoinIosKt.stationsViewModel()`.
///  2. **Seed** from `uiState.value`. `subscribe` does not emit synchronously, so without this the
///     first frame renders an empty screen even though a value already exists.
///  3. **Observe.** `FlowObserverKt.subscribe` (our own `core/common/src/iosMain/.../FlowObserver.kt`)
///     collects the Flow on `Dispatchers.Main` and calls back. Generics erase across the boundary, so
///     the callback hands us `Any?` and we cast exactly once, here.
///  4. **Cancel** in `deinit`. Nothing on iOS calls `ViewModel.onCleared()` — SwiftUI owns this
///     object's lifetime, so if we do not cancel, the collector and its `WhileSubscribed(5_000)`
///     upstream keep running for the life of the process.
///
/// Note what this store does NOT do: it publishes the Kotlin `StationsUiState` directly. That is
/// safe here precisely because `StationCardUi` is flat — pre-formatted `String`s and one enum, with
/// no `Map`, `Set`, or `Instant` to unbox. `HomeStore` is the contrasting case: its state is nested
/// and boxed, so it converts to Swift-native types at this boundary instead.
final class StationsStore: ObservableObject {
    @Published private(set) var state: StationsUiState?

    private let viewModel: StationsViewModel
    private var subscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.stationsViewModel()
        state = viewModel.uiState.value as? StationsUiState
        subscription = FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
            self?.state = value as? StationsUiState
        }
    }

    deinit {
        subscription?.cancel()
    }
}
