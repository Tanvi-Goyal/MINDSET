import SwiftUI
import Shared

/// Root of the iOS app.
///
/// The Android host uses Navigation Compose with type-safe routes (`core/navigation/NavRoutes.kt`)
/// driven by `MindSetNavHost`. iOS has no shared navigation layer — navigation is one of the things
/// KMP deliberately leaves native — so this is a plain SwiftUI `TabView`, and each tab owns its own
/// `NavigationStack`.
///
/// Scope note: Android ships five tabs (Home, Log, Stations, Profile, plus History). iOS currently
/// ports Home and Stations; Profile is here because it was already written against the shared
/// `PreferencesViewModel` and still compiles unchanged. Log and History are not ported — see
/// docs/kmp-ios-notes.md for why (Paging 3 and a 3-deep nested state respectively).
struct ContentView: View {
    @State private var selection = 0
    // One preferences store for the whole app; injected so every screen reads the current unit,
    // and the root drives the color scheme from the theme preference.
    @StateObject private var prefs = PreferencesStore()

    var body: some View {
        TabView(selection: $selection) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)
            StationsView()
                .tabItem { Label("Stations", systemImage: "square.grid.2x2") }
                .tag(1)
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person") }
                .tag(2)
        }
        .environmentObject(prefs)
        .preferredColorScheme(prefs.colorScheme)
        .tint(Obsidian.primary)
    }
}
