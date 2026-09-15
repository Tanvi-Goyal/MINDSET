import SwiftUI
import Shared

/// The Hyrox station board — per-station PB, recent result and trend.
///
/// SwiftUI counterpart of `feature/stations/src/androidMain/kotlin/com/mindset/StationsScreen.kt`.
/// Every label on this screen (`targetLabel`, `recentLabel`, `pbLabel`, `sessionsLabel`, the trend
/// text) is computed in `StationsViewModel` in `commonMain` — this file contains no formatting,
/// no unit conversion and no business rules. Both platforms render byte-identical strings because
/// they are produced by the same Kotlin.
struct StationsView: View {
    @StateObject private var store = StationsStore()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.smd) {
                    BoardHeader()

                    let stations = store.state?.stations ?? []

                    // LazyVGrid(2 fixed columns) is the SwiftUI analogue of Compose's
                    // LazyVerticalGrid(GridCells.Fixed(2)).
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: Space.smd),
                            GridItem(.flexible(), spacing: Space.smd),
                        ],
                        spacing: Space.smd
                    ) {
                        ForEach(stations, id: \.name) { card in
                            StationCardView(card: card)
                        }
                    }

                    if let state = store.state, !state.loading, state.stations.isEmpty {
                        EmptyBoardHint()
                    }
                }
                .padding(.horizontal, Space.md)
                .padding(.bottom, Space.md)
            }
            .background(Obsidian.background)
            .navigationTitle("Stations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Obsidian.background, for: .navigationBar)
        }
    }
}

private struct BoardHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text("COMPETITION PROTOCOL")
                .font(.caption.weight(.semibold))
                .kerning(0.8)
                .foregroundStyle(Obsidian.primary)
            Text("Station Board")
                .font(.title2.bold())
                .foregroundStyle(Obsidian.onSurface)
            Rectangle()
                .fill(Obsidian.primary)
                .frame(height: 2)
                .clipShape(RoundedRectangle(cornerRadius: Radius.xs))
                .padding(.top, Space.xs)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, Space.sm)
    }
}

private struct StationCardView: View {
    let card: StationCardUi

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                Image(systemName: Self.symbol(for: card.station))
                    .font(.system(size: 14))
                    .foregroundStyle(Obsidian.onSurfaceVariant)
                Spacer(minLength: Space.sm)
                Text(card.targetLabel)
                    .font(.caption2)
                    .foregroundStyle(Obsidian.outline)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .multilineTextAlignment(.trailing)
            }

            Spacer().frame(height: Space.md)

            HStack(alignment: .top) {
                Text(card.station.text)
                    .font(.subheadline.bold())
                    .foregroundStyle(Obsidian.onSurface)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let trend = card.trend {
                    TrendChip(trend: trend)
                }
            }

            Spacer().frame(height: Space.md)

            MetricRow(label: "Recent", value: card.recentLabel, valueColor: Obsidian.onSurface)
            Spacer().frame(height: Space.sm)
            MetricRow(label: "PB", value: card.pbLabel, valueColor: Obsidian.primary)

            Spacer().frame(height: Space.md)
            Rectangle().fill(Obsidian.glassBorder).frame(height: 1)
            Spacer().frame(height: Space.md)

            MetricRow(label: "Sessions", value: card.sessionsLabel, valueColor: Obsidian.onSurface)
        }
        .padding(.horizontal, Space.smd)
        .padding(.vertical, Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }

    /// Android maps `HyroxStation` to a custom vector via `UIHelper.hyroxStationIcon`; iOS maps it
    /// to an SF Symbol.
    ///
    /// Note the switch is over `station.name` (the Kotlin enum constant name, a `String`) and needs
    /// a `default` branch. A Kotlin `enum class` does NOT become a Swift `enum` across the
    /// Objective-C boundary — it becomes a class with one singleton class-property per entry. So
    /// Swift gets no exhaustiveness checking here: adding a 9th station silently falls through to
    /// the default instead of failing the build. That erasure is the single most important thing to
    /// understand about consuming Kotlin types from Swift, and it is why `HomeStore` converts to
    /// real Swift enums at the boundary rather than switching on Kotlin ones in the view.
    private static func symbol(for station: HyroxStation) -> String {
        switch station.name {
        case "SKI_ERG": return "figure.skiing.crosscountry"
        case "SLED_PUSH": return "arrow.right.to.line"
        case "SLED_PULL": return "arrow.left.to.line"
        case "BURPEE_BROAD_JUMP": return "figure.jumprope"
        case "ROWING": return "figure.rower"
        case "FARMERS_CARRY": return "dumbbell.fill"
        case "SANDBAG_LUNGES": return "figure.strengthtraining.functional"
        case "WALL_BALLS": return "basketball.fill"
        default: return "circle.dashed"
        }
    }
}

private struct MetricRow: View {
    let label: String
    let value: String
    let valueColor: Color

    var body: some View {
        HStack {
            Text(label.uppercased())
                .font(.caption2)
                .foregroundStyle(Obsidian.onSurfaceVariant)
            Spacer(minLength: Space.xs)
            Text(value)
                .font(.footnote.bold())
                .foregroundStyle(valueColor)
                .lineLimit(1)
        }
    }
}

private struct TrendChip: View {
    let trend: StationTrendUi

    var body: some View {
        let tint = trend.improving ? Obsidian.trendImproving : Obsidian.error
        HStack(spacing: Space.xs) {
            Image(systemName: trend.improving ? "arrow.up.right" : "arrow.down.right")
                .font(.system(size: 9, weight: .bold))
            Text(trend.label)
                .font(.caption2.bold())
        }
        .foregroundStyle(tint)
        .accessibilityLabel(trend.improving ? "Faster than last session" : "Slower than last session")
    }
}

private struct EmptyBoardHint: View {
    var body: some View {
        Text("Set your Hyrox division in Profile to see your station board.")
            .font(.footnote)
            .foregroundStyle(Obsidian.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Space.md)
    }
}
