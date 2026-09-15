import SwiftUI
import Shared

/// The Log tab — SwiftUI counterpart of
/// `feature/logging/src/androidMain/kotlin/com/mindset/{LogTabScreen,LogWorkoutScreen}.kt`.
///
/// Two stores: `LogTabStore` resolves which session is being edited, and a `LogWorkoutStore` keyed to
/// that id does the editing. The keyed store is rebuilt whenever the id changes — Android gets that
/// from `koinViewModel(key = sessionId)`; here it is an explicit `.id()` on the content view.
struct LogView: View {
    private let onOpenProfile: (() -> Void)?

    @StateObject private var tab = LogTabStore()

    init(onOpenProfile: (() -> Void)? = nil) {
        self.onOpenProfile = onOpenProfile
    }

    var body: some View {
        NavigationStack {
            Group {
                if let sessionId = tab.sessionId {
                    LogSessionView(sessionId: sessionId, onCompleted: tab.completed)
                        // Forces a fresh store (and fresh ViewModel) per session.
                        .id(sessionId)
                } else {
                    // Matches Compose's ResolvingPlaceholder: the chrome, an empty body, no spinner.
                    Color.clear
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Obsidian.background)
            .mindSetToolbar(onOpenProfile: onOpenProfile)
        }
    }
}

// MARK: - One session

private struct LogSessionView: View {
    let sessionId: String
    let onCompleted: () -> Void

    @EnvironmentObject private var prefs: PreferencesStore
    @StateObject private var store: LogWorkoutStore

    @State private var showAddSheet = false
    @State private var pendingVariant: HyroxVariant?
    @State private var notes: String = ""

    init(sessionId: String, onCompleted: @escaping () -> Void) {
        self.sessionId = sessionId
        self.onCompleted = onCompleted
        _store = StateObject(wrappedValue: LogWorkoutStore(sessionId: sessionId))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.md) {
                header
                AddStationCta { showAddSheet = true }
                if !store.choices.isEmpty {
                    raceQuickAdd
                }
                ForEach(store.cards) { card in
                    StationCardView(card: card, store: store, unitLabel: unitLabel)
                }
            }
            .padding(Space.md)
        }
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $showAddSheet) {
            AddToSessionSheet(choices: store.choices) { segmentKey in
                showAddSheet = false
                store.addStation(segmentKey)
            }
        }
        .alert(
            "Replace current log?",
            isPresented: Binding(get: { pendingVariant != nil }, set: { if !$0 { pendingVariant = nil } }),
            presenting: pendingVariant
        ) { variant in
            Button("Replace", role: .destructive) {
                store.addVariant(variant, replaceExisting: true)
                pendingVariant = nil
            }
            Button("Keep", role: .cancel) { pendingVariant = nil }
        } message: { variant in
            let count = store.cards.count
            Text("Quick-adding \(RaceVariant.label(variant)) clears the \(count) \(count == 1 ? "entry" : "entries") already in this session.")
        }
        .onAppear { notes = store.header?.notes ?? "" }
        .onChange(of: store.header?.notes ?? "") { _, incoming in
            // The ViewModel debounces writes 400ms and only reflects notes back once the DB
            // round-trips, so adopt an incoming value only when it genuinely differs from the draft.
            if incoming != notes { notes = incoming }
        }
    }

    private var unitLabel: String { Units.shared.label(unit: prefs.units) }

    // MARK: Header

    @ViewBuilder
    private var header: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            HStack(alignment: .top, spacing: Space.sm) {
                VStack(alignment: .leading, spacing: Space.xs) {
                    Text(Self.typeLabel(store.header?.typeName))
                        .labelMediumStyle()
                        .foregroundStyle(Obsidian.primary)
                    if let millis = store.header?.startedAtMillis, millis > 0 {
                        Text(Self.dateText(millis))
                            .labelMediumStyle()
                            .foregroundStyle(Obsidian.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                CompleteCta {
                    store.finish(onDone: onCompleted)
                }
            }

            Text("Active Session")
                .font(MSFont.headlineSmall)
                .foregroundStyle(Obsidian.onSurface)

            GlassTextField(
                placeholder: "Session notes (e.g. Focus on explosive push)",
                text: Binding(
                    get: { notes },
                    set: { value in
                        notes = value
                        store.setNotes(value)
                    }
                )
            )
        }
    }

    // MARK: Quick add

    @ViewBuilder
    private var raceQuickAdd: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            Text("QUICK ADD RACE")
                .labelSmallStyle()
                .foregroundStyle(Obsidian.outline)
            HStack(spacing: Space.sm) {
                ForEach(Array(RaceVariant.all.enumerated()), id: \.offset) { _, variant in
                    VariantChip(title: RaceVariant.label(variant)) {
                        // Replacing wipes every existing entry with no undo, so anything already
                        // logged goes through the confirmation first — as on Android.
                        if store.cards.isEmpty {
                            store.addVariant(variant, replaceExisting: false)
                        } else {
                            pendingVariant = variant
                        }
                    }
                }
            }
        }
    }

    /// `typeLabel` in SessionRow.kt, then uppercased by the header.
    private static func typeLabel(_ typeName: String?) -> String {
        switch typeName {
        case "CONDITIONING": return "CONDITIONING"
        case "HYROX": return "HYROX SIMULATION"
        case "MIXED": return "MIXED"
        default: return "STRENGTH"
        }
    }

    private static func dateText(_ millis: Int64) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy · hh:mm a"
        return formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }
}

// MARK: - Station card

private struct StationCardView: View {
    let card: StationCard
    @ObservedObject var store: LogWorkoutStore
    let unitLabel: String

    // Local capture state. Deliberately not bound straight to the store: every keystroke calls a
    // Kotlin intent which re-emits the whole drafts map back across the main-dispatcher bridge, and
    // a two-way binding against that fights the cursor and can drop characters. One-way out, and
    // re-seeded from the store only when the ViewModel itself changes the draft.
    @State private var timeDigits = ""
    @State private var reps = ""
    @State private var load = ""
    @State private var dist = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Space.smd) {
            HStack(spacing: Space.sm) {
                Image(systemName: Self.symbol(for: card.segmentKey))
                    .font(.system(size: 18))
                    .foregroundStyle(Obsidian.primary)
                Text(card.name)
                    .font(MSFont.titleSmall)
                    .foregroundStyle(Obsidian.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let number = card.stationNumber {
                    Text("STATION \(number)")
                        .labelSmallStyle()
                        .foregroundStyle(Obsidian.outline)
                }
                ConfirmChip(isConfirmed: card.isConfirmed) { store.confirm(card.setId) }
                Button {
                    store.remove(card.id)
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 14))
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove")
            }

            Rectangle().fill(Obsidian.glassBorder).frame(height: 1)

            HStack(alignment: .top, spacing: Space.sm) {
                MetricField(label: "Time (mins)", placeholder: "0:00", text: clockBinding)
                if card.showsReps {
                    MetricField(label: "Reps", placeholder: "0", text: digitBinding($reps, store.setReps))
                }
                if card.showsLoad {
                    MetricField(label: "Load \(unitLabel)", placeholder: "0", text: digitBinding($load, store.setLoad))
                }
                if card.showsDist {
                    MetricField(label: "Dist (m)", placeholder: "0", text: digitBinding($dist, store.setDist))
                }
            }

            if let standard = card.standard {
                StandardHint(text: standard)
            }
        }
        .padding(Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
        .task(id: card.setId) { seed() }
        .onChange(of: store.drafts[card.setId]?.timeDigits ?? "") { _, incoming in
            if incoming != timeDigits { timeDigits = incoming }
        }
    }

    private func seed() {
        let draft = store.drafts[card.setId] ?? DraftValues()
        timeDigits = draft.timeDigits
        reps = draft.reps
        load = draft.load
        dist = draft.dist
    }

    /// The clock is a raw digit buffer, not a text mask: state is the digits, the field shows
    /// `formatClockSec(digitsToSeconds(digits))` — both shared helpers from core/domain/TimeText.kt.
    /// Stripping non-digits in the setter and keeping the last 6 reproduces Compose's
    /// `ClockVisualTransformation`, which pins the cursor to the end so typing always appends and
    /// backspace drops the last digit. Note it renders `m:ss` — minutes are not zero-padded.
    private var clockBinding: Binding<String> {
        Binding(
            get: {
                timeDigits.isEmpty
                    ? ""
                    : TimeTextKt.formatClockSec(sec: Int64(TimeTextKt.digitsToSeconds(digits: timeDigits)))
            },
            set: { typed in
                let digits = String(typed.filter(\.isNumber).suffix(6))
                timeDigits = digits
                store.setTime(card.setId, digits)
            }
        )
    }

    private func digitBinding(_ local: Binding<String>, _ push: @escaping (String, String) -> Void) -> Binding<String> {
        Binding(
            get: { local.wrappedValue },
            set: { typed in
                let digits = typed.filter(\.isNumber)
                local.wrappedValue = digits
                push(card.setId, digits)
            }
        )
    }

    /// `UIHelper.stationIcon` matches on substrings of the segment key; same rule, SF Symbols.
    private static func symbol(for segmentKey: String?) -> String {
        guard let key = segmentKey?.lowercased() else { return "bolt.fill" }
        if key.contains("run") { return "figure.run" }
        if key.contains("ski") { return "figure.skiing.crosscountry" }
        if key.contains("sled") { return "arrow.right.to.line" }
        if key.contains("burpee") { return "figure.jumprope" }
        if key.contains("rowing") { return "figure.rower" }
        if key.contains("farmers") { return "dumbbell.fill" }
        if key.contains("sandbag") || key.contains("lunge") { return "figure.strengthtraining.functional" }
        if key.contains("wall-ball") || key.contains("wall_ball") { return "basketball.fill" }
        return "bolt.fill"
    }
}

// MARK: - Pieces

private struct ConfirmChip: View {
    let isConfirmed: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "checkmark")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(isConfirmed ? Obsidian.onPrimary : Obsidian.onSurfaceVariant)
                .frame(width: 24, height: 24)
                .background(isConfirmed ? Obsidian.primary : Obsidian.surfaceContainerHigh, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isConfirmed ? "Logged" : "Confirm station")
    }
}

private struct StandardHint: View {
    let text: String

    var body: some View {
        HStack(spacing: Space.sm) {
            Image(systemName: "info.circle")
                .font(.system(size: 14))
                .foregroundStyle(Obsidian.primary)
            Text("Standard: \(text)")
                .labelSmallStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
        }
        .padding(.horizontal, Space.smd)
        .padding(.vertical, Space.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Obsidian.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: Radius.sm))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.sm)
                .strokeBorder(Obsidian.primary.opacity(0.25), lineWidth: 1)
        )
    }
}

private struct AddStationCta: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.sm) {
                Image(systemName: "plus")
                    .font(.system(size: 14, weight: .semibold))
                Text("ADD STATION")
                    .labelMediumStyle()
            }
            .foregroundStyle(Obsidian.primary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Space.smd)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md)
                    .strokeBorder(Obsidian.outlineVariant.opacity(0.5), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct VariantChip: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .labelMediumStyle()
                .fontWeight(.semibold)
                .foregroundStyle(Obsidian.primary)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Space.smd)
        }
        .buttonStyle(.plain)
        .glassCard()
    }
}

/// Note the text colour is `onSurface`, not `onPrimary` — near-white on red, as in Compose.
private struct CompleteCta: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Complete Session")
                .labelSmallStyle()
                .fontWeight(.bold)
                .foregroundStyle(Obsidian.onSurface)
                .padding(Space.smd)
                .background(Obsidian.primary, in: RoundedRectangle(cornerRadius: Radius.md))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.md)
                        .strokeBorder(Obsidian.glassBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

private struct AddToSessionSheet: View {
    let choices: [StationChoice]
    let onPick: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.smd) {
                Text("ADD TO SESSION")
                    .labelMediumStyle()
                    .foregroundStyle(Obsidian.onSurfaceVariant)

                if !choices.isEmpty {
                    Text("HYROX STATIONS")
                        .labelSmallStyle()
                        .foregroundStyle(Obsidian.outline)
                        .padding(.top, Space.xs)

                    ForEach(choices) { choice in
                        Button {
                            onPick(choice.segmentKey)
                        } label: {
                            HStack(spacing: Space.smd) {
                                Image(systemName: "bolt.fill")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Obsidian.primary)
                                    .frame(width: 36, height: 36)
                                    .background(Obsidian.surfaceContainerHigh, in: Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(choice.name)
                                        .font(MSFont.titleSmall)
                                        .foregroundStyle(Obsidian.onSurface)
                                    Text(choice.standard)
                                        .labelSmallStyle()
                                        .foregroundStyle(Obsidian.onSurfaceVariant)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(Space.smd)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glassCard()
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, Space.md)
            .padding(.bottom, Space.xl)
            .padding(.top, Space.md)
        }
        .background(Obsidian.surfaceContainerLow)
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }
}
