import Foundation
import Shared

/// Observable adapter around the shared `HomeViewModel`. Same recipe as `HistoryStore`: resolve the
/// VM from Koin, bridge its `uiState` StateFlow into `@Published`, expose intents as methods.
