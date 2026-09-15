import SwiftUI

/// SwiftUI counterparts of the reusable controls in `core/ui/src/main/kotlin/com/mindset/components/`.
/// Kept in one file for the same reason Android keeps them in one package: the onboarding screen is
/// the only caller today, and these must not drift into per-screen variants.

/// `FieldLabel` — uppercase, monospaced, tracked; sits above its control.
struct FieldLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .labelMediumStyle()
            .foregroundStyle(Obsidian.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, Space.xs)
            .padding(.bottom, Space.sm)
    }
}

/// `GlassTextField` — a glass card wrapping a single-line field.
struct GlassTextField: View {
    let placeholder: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default

    var body: some View {
        TextField("", text: $text, prompt: Text(placeholder)
            .foregroundColor(Obsidian.onSurfaceVariant.opacity(0.5)))
            .font(MSFont.bodyLarge)
            .foregroundStyle(Obsidian.onSurface)
            .tint(Obsidian.primary)
            .keyboardType(keyboard)
            .autocorrectionDisabled()
            .padding(.horizontal, Space.md)
            .padding(.vertical, Space.smd)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassCard()
    }
}

/// `StepperField` — –/+ round buttons flanking a centred numeric field.
struct StepperField: View {
    @Binding var value: String
    let onStep: (Int32) -> Void

    var body: some View {
        HStack(spacing: Space.sm) {
            StepButton(symbol: "–") { onStep(-1) }
            TextField("", text: $value, prompt: Text("0")
                .foregroundColor(Obsidian.onSurface.opacity(0.4)))
                .font(MSFont.titleMedium.weight(.bold))
                .foregroundStyle(Obsidian.onSurface)
                .tint(Obsidian.primary)
                .multilineTextAlignment(.center)
                .keyboardType(.decimalPad)
                .frame(maxWidth: .infinity)
            StepButton(symbol: "+") { onStep(1) }
        }
        .padding(Space.sm)
        .glassCard()
    }
}

private struct StepButton: View {
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(symbol)
                .font(MSFont.titleMedium.weight(.bold))
                .foregroundStyle(Obsidian.onSurface)
                .frame(width: 30, height: 30)
                .background(Obsidian.surfaceContainerHigh, in: Circle())
        }
        .buttonStyle(.plain)
    }
}

/// `SegmentedSelector` — a glass track with a solid accent pill on the active option.
/// `selectedIndex == nil` draws no pill, matching Compose's `-1` initial state.
struct SegmentedSelector: View {
    let options: [String]
    let selectedIndex: Int?
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: Space.xs) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                let isActive = index == selectedIndex
                Button {
                    onSelect(index)
                } label: {
                    Text(option)
                        .font(MSFont.titleSmall)
                        .foregroundStyle(isActive ? Obsidian.onPrimary : Obsidian.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Space.sm)
                        // 8pt here is hardcoded in the Compose source too, not shapes.small.
                        .background(isActive ? Obsidian.primary : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(Space.xs)
        .glassCard()
    }
}

/// `PrimaryButton` — solid accent, uppercase label. Disabled state drops to the neutral container.
struct PrimaryButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .labelLargeStyle()
                .fontWeight(.bold)
                .foregroundStyle(enabled ? Obsidian.onPrimary : Obsidian.onSurfaceVariant.opacity(0.5))
                .frame(maxWidth: .infinity)
                .padding(.vertical, Space.smd)
                .padding(.horizontal, Space.sm)
                .background(enabled ? Obsidian.primary : Obsidian.surfaceContainerHigh,
                            in: RoundedRectangle(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// `SecondaryButton` — same box, glass fill, and deliberately **not** uppercased.
struct SecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .labelLargeStyle()
                .fontWeight(.bold)
                .foregroundStyle(Obsidian.onSurface)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Space.smd)
                .padding(.horizontal, Space.sm)
        }
        .buttonStyle(.plain)
        .glassCard()
    }
}

/// A wrapping row — SwiftUI's missing `FlowRow`.
///
/// Compose's `FlowRow` places children left to right and wraps to a new line when the next child
/// would not fit. There is no built-in equivalent, and the difference is not cosmetic: an `HStack`
/// reports the sum of its children as its *minimum* width, so a strip of seven 30pt day tiles forces
/// its card ~258pt wide. Inside a half-width grid cell (~174pt on a 6.3" phone) that card then
/// overflows its column and paints over its neighbour.
///
/// Implemented with the `Layout` protocol rather than a `LazyVGrid` so it measures eagerly and
/// self-sizes inside a card, with no lazy-container height ambiguity.
struct FlowLayout: Layout {
    var horizontalSpacing: CGFloat
    var verticalSpacing: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity

        let rows = rows(maxWidth: maxWidth, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.map(\.height).reduce(0, +)
            + verticalSpacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: min(width, maxWidth), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let rows = rows(maxWidth: bounds.width, subviews: subviews)
        var y = bounds.minY

        for row in rows {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size)
                )
                x += size.width + horizontalSpacing
            }
            y += row.height + verticalSpacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()

        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + horizontalSpacing + size.width

            if needed > maxWidth, !current.indices.isEmpty {
                rows.append(current)
                current = Row()
                current.indices = [index]
                current.width = size.width
                current.height = size.height
            } else {
                current.indices.append(index)
                current.width = needed
                current.height = max(current.height, size.height)
            }
        }

        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

/// `MetricField` from LogWorkoutScreen.kt — a glass box with a mono caps label over a bold numeric
/// field. Digits only, matching the Compose `onValueChange` filter (and the ViewModel filters again).
struct MetricField: View {
    let label: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text(label.uppercased())
                .labelSmallStyle()
                .foregroundStyle(Obsidian.onSurfaceVariant)
            TextField("", text: $text, prompt: Text(placeholder)
                .foregroundColor(Obsidian.onSurfaceVariant.opacity(0.35)))
                .font(MSFont.bodyMedium.weight(.bold))
                .foregroundStyle(Obsidian.onSurface)
                .tint(Obsidian.primary)
                .keyboardType(.numberPad)
        }
        .padding(.horizontal, Space.smd)
        .padding(.vertical, Space.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}
