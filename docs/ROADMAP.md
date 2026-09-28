# MindSet — Roadmap to Hyrox Mumbai (Sept 2026) & Beyond

**Status:** Active · **Owner:** Tanvi Goyal · **Supersedes planning in:** [PRD v0.1](PRD.md) (baseline stale — see below) · **Merges:** PRD v2 + competitor / Hyrox / backend research.

MindSet is an offline-first KMP training tracker. This roadmap turns the portfolio slice into Tanvi's daily-driver app and a beta for friends + HyFit training partners.

**Binding constraint:** Tanvi is user #1, training 5×/week at HyFit for **Hyrox Mumbai, September 2026**. Friends + HyFit partners are users #2–N within ~8 weeks. Auth, multi-device sync, and the Hyrox race-sim/pacing tools are pulled *forward* so race tools get dogfooded before September.

**Product bet — template-first capture:** hybrid athletes train from prescribed structure (whiteboard, program PDF, own plan). MindSet captures the structure once and makes filling in results near-instant. A "class template" and a "self-written program template" are the same object.

**Positioning:** the only offline-first app treating strength, running, and conditioning as equal citizens, capturing whatever plan you already train from, with race-grade Hyrox tools — for markets (like India) where the gym has no API, the Wi-Fi is bad, and the workout is on a whiteboard.

---

## Current state (corrected — PRD baselines are stale)

Already built (both platforms): full logging loop (reps/load/time/distance/RPE), ~870-exercise library with search/filters + muscle diagrams, history + session detail, volume/trend stats, units/theme prefs, a working **sync engine** (outbox push + cursor pull + LWW + soft deletes, DI-wired), `:contracts` shared DTOs, `:server` Ktor, `:benchmark` with baseline profile, and a **complete iOS SwiftUI shell at feature parity**.

**Production gaps:** no auth (server = single anonymous user); server store is **in-memory** (data lost on restart); **destructive** Room migration; demo seed data still present; only `sessions` sync; no CI / crash reporting / privacy policy / export / delete.

## Decisions

1. **Auth: Firebase Auth (buy, not build).** Google (Android) + Apple (iOS) → Firebase ID token as Bearer JWT → Ktor validates statelessly via Google JWKS → `sub` = userId. Overrides PRD's self-issued JWT+refresh design; saved time goes to the Hyrox module.
2. **iOS: keep compiling, port later.** iOS must keep building; new screens land Android-first; iOS parity is a post-race batch.
3. **Backend: keep + harden the custom Ktor sync engine** (the portfolio centerpiece). Postgres behind the existing `SessionStore` interface. Rejected Supabase (free tier pauses after 7d idle; replacing the engine is portfolio-negative) and Firestore (offline cache ≠ source of truth).
4. **Hyrox standards are DATA, never hardcoded** — 25/26 wall-ball rep counts conflict across sources; seed from the official rulebook, verify.
5. **Monetization: subscriptions only — ads deliberately rejected for v1.** RevenueCat subscriptions (`:core:billing`), metered paywall on Stations, entitlement read from Room so Pro survives offline. Ads were considered and dropped: at beta-tester DAU the expected AdMob revenue is smaller than the compliance surface it drags in (UMP consent SDK, GDPR/DMA consent, Play Data Safety redeclaration, `AD_ID` permission, privacy-policy rewrite), and shipping ads alongside a subscription silently reduces the paywall's pitch to "remove the ads" — the weakest value prop available, and one that caps pricing. Revisit only at real scale, and then rewarded-only, never in the workout flow.
6. **hyfit does not expose an API** — its app is a white-label of Spur.fit (no public API/export). The answer is Health Connect import + fast class-template re-logging, not direct integration.

---

## Phases

Each phase ships a runnable vertical slice. 🎓 = learning-critical (own plan-mode pass before `shared/` edits).

### Phase 1 — Production core logging (Weeks 1–3) · *delta on existing code*

> **Data-layer track status** (`data-layer-rethink`): items 1, 6, and the seed part of 7 are **done**,
> plus the PB-detection *engine* (5) and the versioned-migration part of 8. Still open: live-logging
> polish (rest timer, plate calc, swipe — item 3), interval/run logging (4), the PR toast/list UI (5),
> crash reporting + analytics (7), and CI (8). See the `A1–A10` slices below.

1. ✅ 🎓 **Data model v2 migration:** `Session ─< Block ─< ExerciseEntry ─< SetEntry`; `Block.type ∈ {STRAIGHT, SUPERSET, CIRCUIT, INTERVAL, RUN}`; **modality first-class** (`STRENGTH | CONDITIONING | RUN | MOBILITY`) on Exercise, rolled up per Block/Session. Real versioned migration (v7→v8, fixture-tested) + tests; killed destructive fallback; folded legacy `logged_items` into implicit STRAIGHT blocks; UUIDv7 + `deletedAt` tombstones.
2. **Template-first capture:** `is_template` flag; start-from-template pre-fills; duplicate-last-session as cheap v1.
3. **Live logging:** rest timer + notification (🎓 expect/actual), plate calculator, previous-performance ghost values, swipe-to-complete. Sweaty-thumb rule (≤3 taps one-handed).
4. **Interval/run logging:** manual splits, auto pace.
5. **PR detection:** per exercise × rep-range, per distance; toast + PR list. *(engine + persistence done — pure `detectPrs` (Epley e1RM / max-weight / max-reps / best-time-per-bucket / max-calories) runs on set completion; toast + PR list UI still to wire.)*
6. ✅ **Tag all 8 Hyrox stations** as first-class exercises (`hyroxStation` + modality + default metric); catalog re-seeded with modality/metric across ~870 rows.
7. Remove demo seed data ✅; add crash reporting + privacy-first local analytics event log.
8. Cross-cutting: CI (assemble + `commonTest` on PR), versioned migrations, CHANGELOG.

*Non-goals:* auth, backend, social, AI, nutrition, watch.

### Phase 2 — Backend, auth, multi-device sync (Weeks 3–5) · *centerpiece, hardened*
1. 🎓 **Postgres store** behind `SessionStore` (`sync_sessions(id, user_id, payload jsonb, updated_at, deleted, server_seq)`, index on `(user_id, server_seq)`; push = LWW `ON CONFLICT … WHERE excluded.updated_at > …`; pull = `server_seq > cursor`). Flyway; Testcontainers route tests.
2. 🎓 **Firebase Auth** → key every row by userId. Account screen: sign-out-all, **delete account** (cascading).
3. 🎓 **Sync hardening:** WorkManager outbox drain + backoff; sync-on-foreground + debounced sync-after-mutation; FCM pull-nudge; idempotency key per outbox row; `X-Client-Version` → `426`.
4. **Deploy:** Docker in CI → Fly.io Singapore or Lightsail Mumbai; managed Postgres; nightly `pg_dump` → R2 + tested restore runbook; `/metrics` (p50/95/99), `/healthz`, UptimeRobot, Sentry.
5. **CSV/JSON export.**
6. **Exit tests:** device-B convergence; airplane-week replay; **redeploy mid-sync → no dupes**; two-device concurrent edit; tombstone-resurrection.

### Phase 3 — Hyrox framework + BETA (Weeks 5–8) · *differentiator, then real humans*
Build a general `RaceFormat` engine; ship Hyrox as its first fully-fleshed instance (DEKA/CrossFit = future data rows).
1. 🎓 **Framework:** `RaceFormat` (ordered run/station/transition segments + division params, all data); guided session runner (glanceable timer, per-segment splits); benchmark session type (repeatable test → PB board); pacing calculator (goal time → per-segment splits → shareable/printable pacing card); explainable finish-time predictor v1.
2. **Hyrox instance:** `HyroxStation` + `DivisionStandard` seed (verified against 25/26 rulebook); 8 stations + 8×1km + Roxzone; full/half/pair sims; **compromised-running insight** (per-run delta vs fresh 1km pace + fade curve); `HyroxRace` (synced) + `RaceSplit` (manual, `source` field); per-station PR board.
3. **Beta:** Play internal testing (≤100) + Firebase App Distribution; onboarding ("what are you training for?" → persona templates, logging in <2 min); privacy policy + data-safety + export + delete; shake-to-report + WhatsApp group + weekly builds; perf gate (cold start <800ms, jank-free logging in CI).

**→ September — Hyrox Mumbai:** pacing card used live; official splits entered → predicted-vs-actual retro → blog post + backlog.

### Phase 4 — Insights & training load (Months 3–4)
Weekly training report (Sunday ritual — volume by modality, sessions vs plan, PBs, one insight; on-device `weekly_summary` table); ACWR training-load model (explainable); balance radar (modality + movement-pattern staleness; per-station for racers); run analytics + lightweight body/context log; body heatmap on muscle diagram; weekly-streak calendar + Glance widget. Start Health Connect permission paperwork.

### Phase 5 — Health Connect & wearable import (Months 4–5) · *the hyfit answer*
🎓 HC read path (`ExerciseSessionRecord` → changes-token incremental → dedupe → source-tagged local writes through outbox; HealthKit mirror on iOS); template matching against a class-schedule timetable; write completed sessions back to HC; hyfit quick-log templates.

### Phase 6 — Programs, polish, wider beta (Months 5–6)
🎓 Program engine (`Program(weeks × days × slots × SetScheme)` sealed types + `ProgressionRule`; seed 5/3/1, PPL, a 12-week Hyrox block); **iOS parity batch** + TestFlight; widgets + share cards + monthly report; Play open testing → production.

### Deferred backlog
Warm-up generator · exercise videos · coach mode (HyFit B2B) · official Hyrox results import · Doubles shared sessions · nutrition integration · AI whiteboard→template OCR · Wear OS tile · social feed.

---

## Verification (per phase)
`./gradlew :shared:testAndroidHostTest :server:test`, `:composeApp:assembleDebug`, `:shared:iosSimulatorArm64Test` (iOS keeps compiling), run on device.
- **P1:** legacy sessions survive v7→v8 migration (fixture-DB test — `MigrationTest`, iosTest); full HyFit session logged offline.
- **P2:** kill server mid-sync → zero data loss; wipe + reinstall + sign in → full restore ("new phone"); two devices → LWW converges; redeploy mid-sync → no dupes.
- **P3:** full Hyrox sim in-app ≥2 weeks pre-race; pacing card renders + prints.
- CI gates from P1; Macrobenchmark cold-start + jank gate from P3; server p99 per route.
