import Foundation
import Shared

/// Observable adapter around the shared `ProfileViewModel`.
///
/// Pattern A (direct publish): `ProfileUiState` is the flattest state in the codebase — booleans,
/// ints, strings and one list of flat structs. Nothing to unbox.
///
/// The `deinit` cancel matters more here than elsewhere: `ProfileViewModel` runs an infinite
/// `flow { … delay(…) }` midnight ticker to roll the streak over. It is held open by
/// `WhileSubscribed(5_000)`, and nothing on iOS calls `onCleared()` — so a leaked subscription means
/// that coroutine lives for the life of the process.
final class ProfileStore: ObservableObject {
    @Published private(set) var state: ProfileUiState?

    private let viewModel: ProfileViewModel
    private var subscription: FlowSubscription?

    init() {
        viewModel = KoinIosKt.profileViewModel()
        state = viewModel.uiState.value as? ProfileUiState
        subscription = FlowObserverKt.subscribe(flow: viewModel.uiState) { [weak self] value in
            self?.state = value as? ProfileUiState
        }
    }

    deinit {
        subscription?.cancel()
    }
}
