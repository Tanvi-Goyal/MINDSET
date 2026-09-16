import SwiftUI

/// "Obsidian Performance" design tokens, ported by hand from the Android design system
/// (`core/designsystem/src/main/kotlin/com/mindset/{Color,Spacing,Shape}.kt`).
///
/// These are deliberately NOT shared through KMP. `:core:designsystem` is a plain Android library —
/// its tokens are Compose types (`Color`, `Dp`, `RoundedCornerShape`) that mean nothing to SwiftUI,
/// and sharing them would mean dragging Compose Multiplatform's UI layer into the iOS framework
/// purely to hold constants. The hex values are the contract; each platform expresses them in its
/// own primitives. Keeping the two files in sync by hand is the accepted cost.
enum Obsidian {
    // Surfaces
    static let background = Color(hex: 0x131313)
    static let surface = Color(hex: 0x131313)
    static let surfaceContainerLow = Color(hex: 0x1C1B1B)
    static let surfaceContainer = Color(hex: 0x201F1F)
    static let surfaceContainerHigh = Color(hex: 0x2A2A2A)
    /// Deepest ground — the bottom stop of the simulation-tile scrim.
    static let surfaceContainerLowest = Color(hex: 0x0E0E0E)

    // Content
    static let onSurface = Color(hex: 0xE5E2E1)
    static let onSurfaceVariant = Color(hex: 0xE7BDB7)
    static let outline = Color(hex: 0xAD8883)
    static let outlineVariant = Color(hex: 0x5D3F3B)

    /// Electric red. Note the deliberate deviation documented in Color.kt: a stock M3 dark export
    /// puts the pale salmon in `primary` and the saturated red in `primaryContainer`. MindSet swaps
    /// them, because every button in the design is solid red with white text.
    static let primary = Color(hex: 0xE5484D)
    static let onPrimary = Color.white
    /// Selected bottom-nav tint. BottomNavBar.kt uses `primaryContainer`, not `primary`.
    static let primaryContainer = Color(hex: 0xFF3B30)
    /// Unselected bottom-nav tint.
    static let onSecondaryContainer = Color(hex: 0xB4B5B5)
    /// Medallion fill behind a session row's type icon.
    static let secondaryContainer = Color(hex: 0x454747)
    /// Pale salmon accent — frequency bars for the current week, PRO tags, metric tints.
    static let coral = Color(hex: 0xFFB4AA)
    static let error = Color(hex: 0xFFB4AB)
    static let trendImproving = Color(hex: 0x7CD672)
    /// Pill text over the simulation photographs.
    static let floatingTagText = Color(hex: 0xCBEF97)

    /// The glass treatment: a 5% white fill behind a 12% white hairline border.
    static let glassFill = Color.white.opacity(0.05)
    static let glassBorder = Color.white.opacity(0.12)
}

/// Mirrors `Spacing.kt`. Same scale, same names — so a layout can be read side by side with its
/// Compose counterpart.
enum Space {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let smd: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
}

/// Mirrors `Shape.kt` (corner radii only — SwiftUI applies them per-view).
enum Radius {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// The card treatment used across the app — `GlassFill` + `GlassBorder` from Color.kt.
struct GlassCard: ViewModifier {
    var radius: CGFloat = Radius.md

    func body(content: Content) -> some View {
        content
            .background(Obsidian.glassFill, in: RoundedRectangle(cornerRadius: radius))
            .overlay(
                RoundedRectangle(cornerRadius: radius)
                    .strokeBorder(Obsidian.glassBorder, lineWidth: 1)
            )
    }
}

extension View {
    func glassCard(radius: CGFloat = Radius.md) -> some View {
        modifier(GlassCard(radius: radius))
    }
}


// MARK: - Type scale
//
// Mirrors `core/designsystem/src/main/kotlin/com/mindset/ObsidianType.kt`. Android bundles Inter for
// most roles and **JetBrains Mono for labelMedium / labelSmall** — every uppercase micro-label in the
// app is monospaced, which is a big part of how the UI reads. iOS uses the system faces (SF Pro and
// SF Mono via `design: .monospaced`) rather than bundling Inter and JetBrains Mono: the platform
// faces are what makes an iOS app feel native, and the *role* — monospaced caps at a given tracking —
// is what carries the design, not the specific family.
enum MSFont {
    static let displaySmall = Font.system(size: 36, weight: .bold)
    static let headlineSmall = Font.system(size: 24, weight: .bold)
    /// Between headlineSmall and displaySmall — the stat-tile value size on History and Detail.
    static let headlineMedium = Font.system(size: 28, weight: .bold)
    static let titleMedium = Font.system(size: 20, weight: .semibold)
    static let titleSmall = Font.system(size: 16, weight: .semibold)
    static let bodyLarge = Font.system(size: 16)
    static let bodyMedium = Font.system(size: 14)
    static let bodySmall = Font.system(size: 12)
    /// Inter/SemiBold, 0.06em tracking.
    static let labelLarge = Font.system(size: 14, weight: .semibold)
    /// Mono/Medium, 0.1em tracking.
    static let labelMedium = Font.system(size: 12, weight: .medium, design: .monospaced)
    /// Mono/Medium, 0.1em tracking.
    static let labelSmall = Font.system(size: 11, weight: .medium, design: .monospaced)

    // Tracking in points, since SwiftUI's `kerning` is absolute where Compose's letterSpacing is em.
    static let labelLargeTracking: CGFloat = 14 * 0.06
    static let labelMediumTracking: CGFloat = 12 * 0.1
    static let labelSmallTracking: CGFloat = 11 * 0.1
}

extension Text {
    /// The app's uppercase micro-label: monospaced, tracked out. Compose gets this from
    /// `MaterialTheme.typography.labelMedium`; here it is spelled once so it can't drift.
    func labelMediumStyle() -> Text {
        self.font(MSFont.labelMedium).kerning(MSFont.labelMediumTracking)
    }

    func labelSmallStyle() -> Text {
        self.font(MSFont.labelSmall).kerning(MSFont.labelSmallTracking)
    }

    func labelLargeStyle() -> Text {
        self.font(MSFont.labelLarge).kerning(MSFont.labelLargeTracking)
    }
}

// MARK: - Brand

/// The MIND[SET] lockup: `MIND` and `SET` in white, the two brackets in the electric-red primary.
///
/// Android centralises this in `core/ui/src/main/kotlin/com/mindset/AppBrand.kt` so the splash lockup
/// and the top bar can never diverge; this is the same idea for the three iOS screens that show it.
/// It is the app's title bar — a plain `.navigationTitle("MindSet")` is not the same thing.
struct Wordmark: View {
    var size: CGFloat = 20

    var body: some View {
        (
            Text("MIND").foregroundColor(.white)
            + Text("[").foregroundColor(Obsidian.primary)
            + Text("SET").foregroundColor(.white)
            + Text("]").foregroundColor(Obsidian.primary)
        )
        .font(.system(size: size, weight: .bold))
        .accessibilityLabel("MindSet")
    }
}

/// `MindSetSectionHeader` from `core/ui/.../SectionHeader.kt`: a 3×14pt accent tick, then the title
/// in tracked uppercase, with an optional trailing control.
struct SectionHeader<Trailing: View>: View {
    private let title: String
    private let trailing: () -> Trailing

    init(title: String, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            RoundedRectangle(cornerRadius: Radius.xs)
                .fill(Obsidian.primary)
                .frame(width: 3, height: 14)
            Text(title.uppercased())
                .labelLargeStyle()
                .foregroundStyle(Obsidian.onSurface)
                .padding(.leading, Space.sm)
            Spacer(minLength: Space.sm)
            trailing()
        }
    }
}

extension SectionHeader where Trailing == EmptyView {
    init(title: String) {
        self.init(title: title) { EmptyView() }
    }
}
