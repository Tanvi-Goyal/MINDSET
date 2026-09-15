import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Start the shared Koin graph before any screen resolves dependencies.
        // No Context needed on iOS — the DB builder uses a Documents-directory path.
        KoinIosKt.doInitKoin()
        // SwiftUI's toolbar modifiers do not reach UIKit's scroll-edge appearances, so pin the
        // Obsidian ground onto both bars here as well.
        BarAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
