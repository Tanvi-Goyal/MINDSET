import SwiftUI
import Shared

/// Home screen — summary stats, the planned session, and recent sessions, all from the shared
/// `HomeViewModel.uiState`.
/// Destinations reachable from the Home logging flow.
enum LogRoute: Hashable {
    case newSession
    case logWorkout(String)
    case sessionDetail(String)
}
