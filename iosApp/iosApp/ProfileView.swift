import SwiftUI
import Shared

/// Preferences screen — weight units + theme, backed by the shared `PreferencesViewModel` via the
/// app-wide `PreferencesStore`. Changes persist to the DB and flow back to every screen.
struct ProfileView: View {
    @EnvironmentObject private var prefs: PreferencesStore

    var body: some View {
        NavigationStack {
            Form {
                Section("Weight units") {
                    Picker("Units", selection: Binding(
                        get: { prefs.units },
                        set: { prefs.setUnit($0) }
                    )) {
                        Text("Kilograms (kg)").tag(WeightUnit.kg)
                        Text("Pounds (lb)").tag(WeightUnit.lb)
                    }
                    .pickerStyle(.segmented)
                }
                Section("Theme") {
                    Picker("Theme", selection: Binding(
                        get: { prefs.themeMode },
                        set: { prefs.setTheme($0) }
                    )) {
                        Text("System").tag(ThemeMode.system)
                        Text("Light").tag(ThemeMode.light)
                        Text("Dark").tag(ThemeMode.dark)
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("Profile")
        }
    }
}
