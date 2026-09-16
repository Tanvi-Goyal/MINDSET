import SwiftUI

/// One session in a list — `SessionRow` from `core/ui/.../components/SessionRow.kt`.
///
/// Shared by Home's Recent Sessions and History's Recent list, exactly as on Android. Note it renders
/// **no volume**: `volumeKg` is a parameter on the Compose version that its body never reads, and
/// History passes `0.0` for it explicitly. Duration and the optional PB pill are the only trailing
/// content.
struct SessionRowModel: Identifiable {
    let id: String
    let name: String
    /// The Kotlin `SessionType` constant name, carried as a String so views never name an Obj-C enum
    /// entry.
    let typeName: String
    let startedAtMillis: Int64
    let durationSec: Int?
    /// Only ever true on the Pro history path, which iOS does not render yet — the pill is here so
    /// the row is complete when that lands.
    let isPb: Bool

    init(
        id: String,
        name: String,
        typeName: String,
        startedAtMillis: Int64,
        durationSec: Int?,
        isPb: Bool = false
    ) {
        self.id = id
        self.name = name
        self.typeName = typeName
        self.startedAtMillis = startedAtMillis
        self.durationSec = durationSec
        self.isPb = isPb
    }
}

struct SessionRowView: View {
    let session: SessionRowModel
    var onTap: (() -> Void)?

    var body: some View {
        Button {
            onTap?()
        } label: {
            HStack(spacing: Space.md) {
                Image(systemName: SessionIcons.typeSymbol(session.typeName))
                    .font(.system(size: 16))
                    .foregroundStyle(Obsidian.onSurfaceVariant)
                    .frame(width: 16, height: 16)
                    .padding(Space.sm)
                    .background(Obsidian.secondaryContainer, in: Circle())

                VStack(alignment: .leading, spacing: Space.xs) {
                    Text(session.name)
                        .font(MSFont.bodyMedium.weight(.bold))
                        .foregroundStyle(Obsidian.onSurface)
                        .lineLimit(1)
                    Text(Format.relativeDay(session.startedAtMillis))
                        .labelMediumStyle()
                        .foregroundStyle(Obsidian.onSurfaceVariant)
                }

                Spacer(minLength: Space.sm)

                VStack(alignment: .trailing, spacing: Space.sm) {
                    if let seconds = session.durationSec, seconds > 0 {
                        Text(Format.duration(seconds))
                            .font(MSFont.bodyMedium)
                            .foregroundStyle(Obsidian.onSurface)
                    }
                    if session.isPb {
                        Text("PB")
                            .labelSmallStyle()
                            .fontWeight(.bold)
                            .foregroundStyle(Obsidian.primary)
                            .padding(.horizontal, Space.sm)
                            .padding(.vertical, Space.xs)
                            .background(Obsidian.primary.opacity(0.15), in: Capsule())
                    }
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Obsidian.onSurfaceVariant)
                    .frame(width: 32, height: 32)
            }
            .padding(Space.md)
            .glassCard()
            .padding(.vertical, Space.xs)
        }
        .buttonStyle(.plain)
        .disabled(onTap == nil)
    }
}

/// Icon lookups shared across the session screens.
enum SessionIcons {
    /// `typeIcon` in SessionRow.kt: conditioning → run, hyrox → lightning, mixed → bolt, else dumbbell.
    static func typeSymbol(_ typeName: String) -> String {
        switch typeName {
        case "CONDITIONING": return "figure.run"
        case "HYROX": return "bolt.fill"
        case "MIXED": return "bolt.horizontal.fill"
        default: return "dumbbell.fill"
        }
    }

    /// `typeLabel` in SessionRow.kt.
    static func typeLabel(_ typeName: String) -> String {
        switch typeName {
        case "CONDITIONING": return "Conditioning"
        case "HYROX": return "Hyrox Simulation"
        case "MIXED": return "Mixed"
        default: return "Strength"
        }
    }

    /// `UIHelper.stationIcon` matches on substrings of the segment key; same rule, SF Symbols.
    static func stationSymbol(_ segmentKey: String?) -> String {
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

/// `HeaderIconButton` from `core/ui/.../components/HeaderIconButton.kt` — a 40pt circle on the
/// neutral container. Session Detail's back affordance is one of these in the content, not a toolbar
/// item, so it scrolls away with the page.
struct HeaderIconButton: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Obsidian.onSurface)
                .frame(width: 40, height: 40)
                .background(Obsidian.surfaceContainerHigh, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
