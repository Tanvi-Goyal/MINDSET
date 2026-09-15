import SwiftUI
import Shared

/// Root of the iOS app.
///
/// Mirrors the decision `MindSetNavHost.kt` makes on Android: Splash → Onboarding when setup is
/// incomplete, otherwise the tabbed app. Navigation itself is not shared — Android uses type-safe
/// Navigation Compose routes, iOS uses `TabView` + per-tab `NavigationStack`.
///
/// Tab order matches `BottomNavTab` (Home, Log, Stations, Profile). History and Session Detail are
/// pushed destinations on Android and are not ported yet.
struct ContentView: View {
    @State private var selection = 0
    @State private var splashDone = false
    @StateObject private var prefs = PreferencesStore()

    var body: some View {
        ZStack {
            // Painted under everything so no transition ever exposes the host's white ground.
            Obsidian.background.ignoresSafeArea()

            if splashDone, let complete = prefs.isOnboardingComplete {
                // Splash → Onboarding or Home, the same decision MindSetNavHost makes on Android.
                if complete {
                    tabs
                } else {
                    OnboardingView()
                }
            } else {
                SplashView {
                    // 360ms, matching `fadeOut(tween(360))` on the Compose Splash destination.
                    withAnimation(.easeOut(duration: 0.36)) { splashDone = true }
                }
                .transition(.opacity)
            }
        }
        .environmentObject(prefs)
        // Unconditional: MindSetTheme is dark-only, so there is nothing to follow the system for.
        .preferredColorScheme(.dark)
    }

    private let profileTab = 3

    private var tabs: some View {
        TabView(selection: $selection) {
            HomeView(onOpenProfile: { selection = profileTab })
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(0)
            LogView(onOpenProfile: { selection = profileTab })
                .tabItem { Label("Log", systemImage: "plus.circle.fill") }
                .tag(1)
            StationsView(onOpenProfile: { selection = profileTab })
                .tabItem { Label("Stations", systemImage: "square.grid.2x2.fill") }
                .tag(2)
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.fill") }
                .tag(profileTab)
        }
        // BottomNavBar.kt tints the selected item with `primaryContainer`, not `primary`.
        .tint(Obsidian.primaryContainer)
    }
}

/// The Obsidian ground has to be pinned onto UIKit's bar appearances as well as SwiftUI's, or the
/// bars revert to the system material as soon as content scrolls under them.
enum BarAppearance {
    static func apply() {
        let ground = UIColor(Obsidian.background)

        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = ground
        nav.shadowColor = .clear
        nav.titleTextAttributes = [.foregroundColor: UIColor(Obsidian.onSurface)]
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor(Obsidian.onSurface)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = ground
        tab.shadowColor = UIColor(Obsidian.glassBorder)
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab
        UITabBar.appearance().unselectedItemTintColor = UIColor(Obsidian.onSecondaryContainer)
    }
}

/// The MIND[SET] top bar, shared by every tab — the counterpart of `MindSetTopBar` in
/// `core/ui/src/main/kotlin/com/mindset/components/TopAppBar.kt`, which puts the wordmark in the
/// title slot and a profile action on the trailing edge.
struct MindSetToolbar: ViewModifier {
    var onOpenProfile: (() -> Void)?

    func body(content: Content) -> some View {
        content
            .toolbar {
                ToolbarItem(placement: .principal) { Wordmark() }
                if let onOpenProfile {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(action: onOpenProfile) {
                            Image(systemName: "person.crop.circle")
                                .foregroundStyle(Obsidian.onSurface)
                        }
                    }
                }
            }
            // Without this the bar defaults to `.large`, which reserves an empty large-title strip
            // under the toolbar — a gap on every screen, since the title slot holds the wordmark as a
            // principal item rather than a navigationTitle.
            .navigationBarTitleDisplayMode(.inline)
            // `.toolbarBackground(color, for:)` alone is inert — the `.visible` call is what pins it.
            .toolbarBackground(Obsidian.background, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbarBackground(Obsidian.background, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

extension View {
    func mindSetToolbar(onOpenProfile: (() -> Void)? = nil) -> some View {
        modifier(MindSetToolbar(onOpenProfile: onOpenProfile))
    }
}
