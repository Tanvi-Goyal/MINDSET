import SwiftUI
import Shared

struct ContentView: View {
    @State private var selection = 0
    // One preferences store for the whole app; injected so every screen reads the current unit,
    // and the root drives the color scheme from the theme preference.
    @StateObject private var prefs = PreferencesStore()

    var body: some View {
        
    }
}
