# AGENTS.md — MindSet

Cross-platform training tracker. Offline-first. KMP for shared logic; native UI
(Jetpack Compose on Android, a thin SwiftUI shell on iOS). This is a portfolio
project built to learn internals — defensible decisions and code quality matter
more than feature count.

Canonical agent brief. Claude Code reads it via `@AGENTS.md` in CLAUDE.md; other
tools read it directly. Keep it lean — every line must change agent behavior.

## Project snapshot
- Modules — Android host + KMP shared logic, split into `:core:*` and `:feature:*`:
  - `:app` — Android entry point: Compose host + navigation wiring; depends on the
    UI cores + every feature. No business logic.
  - `:shared` — KMP aggregator + Koin DI (`di/Modules.kt`). api-exports the core +
    feature modules so the app and the iOS framework consume one dependency. Holds
    NO domain/data/UI itself (that moved into `:core:*` and `:feature:*`).
  - `:core:*` — shared logic. Pure KMP: `:core:model`, `:core:common`, `:core:domain`.
    Offline-first data layer (KMP + androidMain/iosMain seams): `:core:database`,
    `:core:network`, `:core:data`, `:core:sync`. Android-only Compose: `:core:designsystem`
    (theme/spacing tokens), `:core:ui` (shared components), `:core:navigation`.
  - `:feature:*` — one module per feature (profile, stats, history, exercises, logging,
    templates, home, auth, onboarding). KMP: ViewModels/MVI state in commonMain,
    Compose screens in androidMain.
  - `:contracts` (shared wire DTOs, client + server) · `:server` (Ktor sync backend) ·
    `:benchmark` (Macrobenchmark + Baseline Profile). `iosApp/` is a SwiftUI shell over the
    shared ViewModels, behind since Aug 2026 (Home widgets, Stations); parity batch pending.
- Stack: Kotlin 2.4 (K2), Room-KMP, Ktor 3, Koin, Coroutines/Flow, Compose.
  Versions live in `gradle/libs.versions.toml` — read there, never guess.
- Backend: custom Ktor sync engine (outbox push / cursor pull / LWW / soft
  deletes). Being hardened — Postgres + Firebase-Auth JWT — NOT replaced by a
  BaaS (Supabase/Firestore rejected; see `docs/ROADMAP.md`). Server store is
  currently in-memory; Postgres lands in Phase 2.
- Architecture: offline-first, DB as single source of truth; MVI (unidirectional
  state) in shared ViewModels, exposed to UI as StateFlow.
- Direction: see `docs/ROADMAP.md` — daily-driver + beta for Hyrox Mumbai
  (Sept 2026); template-first capture; Hyrox race module; Health Connect import.

## Build & run
- `./gradlew :app:assembleDebug`             # Android debug build
- `./gradlew :shared:testAndroidHostTest`    # shared unit tests (androidLibrary
  Gradle plugin uses "hostTest" terminology, not the classic testDebugUnitTest)
- `./gradlew :shared:iosSimulatorArm64Test`  # shared tests on iOS (Room integration
  tests live in iosTest — the no-arg in-memory DB builder is Context-free on native)
- `./gradlew :server:run`                     # start the Ktor sync backend on :8080
- `./gradlew :server:test`                    # sync route tests (LWW / cursor / soft delete)
- `./gradlew :app:generateBaselineProfile`   # generate + package the baseline profile (needs a device)
- `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`  # run Macrobenchmarks (Startup/Scroll) on a device
  # benchmark plugin pinned to 1.5.0-alpha06 — 1.4.1 needs the legacy TestExtension which AGP 9's new DSL dropped

## Operating contract — how to work with me
I am building this to learn, and I must be able to defend every decision in an
interview. So:
1. Explain the WHY before the HOW. Never hand me code I haven't been walked
   through. Teach, don't just produce.
2. Before non-trivial work, propose 2-3 approaches with explicit tradeoffs
   (performance, complexity, testability, scalability) and wait for my decision.
3. Prefer the smallest diff that solves the problem. Do not refactor, rename, or
   reformat unrelated code. Touch the fewest files.
4. When intent or a tradeoff is ambiguous, ask one focused question rather than
   assume. Prefer asking over guessing.
5. For any choice involving threading, recomposition, allocation, DB access, or
   data size, state the performance/scalability implication explicitly.
6. Cite the authoritative source for non-obvious platform claims:
   developer.android.com, kotlinlang.org, cs.android.com (AOSP), and the
   official Room/Compose/Ktor/coroutines docs.
7. I implement or we pair on the learning-critical pieces (sync engine,
   expect/actual seams, recomposition-sensitive UI). You may draft boilerplate —
   but flag which parts are learning-critical and slow down there.

## Architecture rules (non-negotiable)
- The UI never reads the network. It observes the DB via Flow. Network only
  feeds the DB (pull) and drains the outbox (push).
- Every synced entity carries: `id` (client-generated UUIDv7), `createdAt`,
  `updatedAt` (LWW key), `deletedAt` (soft-delete tombstone; null = live).
  `syncStatus` is a LOCAL-only column (never crosses the wire). Local write +
  outbox enqueue in ONE transaction.
- `commonMain` = domain + data + sync + ViewModels. Platform code
  (androidMain/iosMain) is only for OS-governed capabilities: DB driver, health
  APIs, background scheduling, notifications, secure storage. Justify each new seam.
- Ship feature-by-feature to a RUNNABLE state. A thin complete vertical slice
  beats a broad half-built layer.

## Conventions
- Kotlin official style; explicit visibility on public API; no `!!` in shared code.
- One logical change per commit; imperative commit messages.
- New public shared API gets KDoc explaining intent, not restating the signature.

## Prefer / avoid
- Prefer immutable state + unidirectional data flow over mutable shared state.
- Prefer `StateFlow` + `stateIn` over exposing raw flows to the UI.
- Prefer naming what a new dependency replaces over adding one silently.
- Prefer building for the current MVP with clean seams over speculative generality.
