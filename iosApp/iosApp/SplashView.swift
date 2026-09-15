import SwiftUI

/// Branded splash — SwiftUI counterpart of `app/src/main/kotlin/com/mindset/SplashScreen.kt`.
///
/// Same composition as Compose: the `AppBrand` lockup (logo, 28pt gap, wordmark) centred, a 36pt gap,
/// then a 200×4 sheen bar. Holds for `splashDuration` and then hands back, and `ContentView` fades it
/// out over 360ms — matching the `fadeOut(tween(360))` exit transition on the Splash destination.
///
/// The hold is not just decoration here: device-local preferences are read asynchronously, so this is
/// also what stops a returning user seeing onboarding flash past before `isOnboardingComplete` arrives.
struct SplashView: View {
    /// `SPLASH_DURATION_MS` in SplashScreen.kt.
    static let splashDuration: Duration = .milliseconds(1000)

    let onDone: () -> Void

    var body: some View {
        ZStack {
            Obsidian.background.ignoresSafeArea()
            VStack(spacing: 0) {
                BrandLogo(size: 108)
                Spacer().frame(height: 28)
                Wordmark(size: 36)
                Spacer().frame(height: 36)
                SheenProgress()
                    .frame(width: 200, height: 4)
            }
        }
        .task {
            try? await Task.sleep(for: Self.splashDuration)
            onDone()
        }
    }
}

/// The MIND[SET] mark, redrawn from `core/ui/src/main/res/drawable/brand_logo.xml`.
///
/// Redrawn rather than exported: the Android asset is a `<vector>`, which iOS cannot read, and the
/// geometry is simple enough that a Shape stays crisp at any size and needs no @2x/@3x bitmaps. All
/// coordinates below are in the original 108×108 viewport and scaled once, so they can be diffed
/// against the drawable directly.
struct BrandLogo: View {
    var size: CGFloat = 108

    private static let viewport: CGFloat = 108
    /// Bracket stroke thickness and arm length, from the drawable's path coordinates.
    private static let barWidth: CGFloat = 3.24
    private static let armLength: CGFloat = 8.10
    private static let top: CGFloat = 39.42
    private static let bottom: CGFloat = 68.58

    var body: some View {
        let s = size / Self.viewport

        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 24.16 * s)
                .fill(Color(hex: 0x1B1C20))
                .frame(width: size, height: size)

            // Left bracket "["
            bracketBar(x: 36.18, y: Self.top, w: Self.barWidth, h: Self.bottom - Self.top, s: s)
            bracketBar(x: 36.18, y: Self.top, w: Self.armLength, h: Self.barWidth, s: s)
            bracketBar(x: 36.18, y: 65.34, w: Self.armLength, h: Self.barWidth, s: s)

            // Right bracket "]"
            bracketBar(x: 68.58, y: Self.top, w: Self.barWidth, h: Self.bottom - Self.top, s: s)
            bracketBar(x: 63.72, y: Self.top, w: Self.armLength, h: Self.barWidth, s: s)
            bracketBar(x: 63.72, y: 65.34, w: Self.armLength, h: Self.barWidth, s: s)

            // The ECG trace between the brackets.
            ECGTrace()
                .stroke(
                    Obsidian.primary,
                    style: StrokeStyle(lineWidth: 3.46 * s, lineCap: .round, lineJoin: .round)
                )
                .frame(width: size, height: size)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func bracketBar(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, s: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 1.62 * s)
            .fill(Color.white)
            .frame(width: w * s, height: h * s)
            .offset(x: x * s, y: y * s)
    }
}

/// The heartbeat polyline from the drawable, in the same 108×108 viewport.
private struct ECGTrace: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 108
        let points: [CGPoint] = [
            CGPoint(x: 44.28, y: 54.54),
            CGPoint(x: 48.82, y: 54.54),
            CGPoint(x: 51.84, y: 43.74),
            CGPoint(x: 56.16, y: 65.34),
            CGPoint(x: 59.18, y: 54.54),
            CGPoint(x: 63.72, y: 54.54),
        ].map { CGPoint(x: $0.x * s, y: $0.y * s) }

        var path = Path()
        path.addLines(points)
        return path
    }
}

/// `SheenProgress` from `core/ui/.../SheenProgress.kt`: a lens-shaped bar with a soft base gradient
/// and a bright band sweeping left → right on a 1.8s loop.
struct SheenProgress: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width

            ZStack(alignment: .leading) {
                LinearGradient(
                    colors: [.clear, .white.opacity(0.45), .clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )

                // Travelling highlight. Compose sweeps the band centre from -0.3w to 1.3w.
                LinearGradient(
                    colors: [.clear, .white.opacity(0.95), .clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: w * 0.48)
                .offset(x: (-0.3 + 1.6 * phase) * w - w * 0.24)
            }
            .mask(LensShape())
        }
        .onAppear {
            guard !UIAccessibility.isReduceMotionEnabled else { return }
            withAnimation(.linear(duration: 1.8).repeatForever(autoreverses: false)) {
                phase = 1
            }
        }
    }
}

/// The pointed-oval clip: two quadratic curves meeting at the left and right edges.
private struct LensShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        let cy = rect.midY

        var path = Path()
        path.move(to: CGPoint(x: 0, y: cy))
        path.addQuadCurve(to: CGPoint(x: w, y: cy), control: CGPoint(x: w * 0.5, y: cy - h))
        path.addQuadCurve(to: CGPoint(x: 0, y: cy), control: CGPoint(x: w * 0.5, y: cy + h))
        path.closeSubpath()
        return path
    }
}
