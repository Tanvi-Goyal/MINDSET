# KMP: Android → iOS, mapped onto MindSet

Working notes for the iOS slice. Everything here is grounded in this repo — file paths are real,
and the claims are things you can point at in the code rather than recite.

---

## 1. Terminology map

| Android / Kotlin | iOS / Kotlin-Native | Notes |
|---|---|---|
| Gradle module (`:feature:home`) | — | iOS has no module concept here; every module links into **one** framework |
| `commonMain` | `commonMain` | compiled twice: to JVM bytecode and to a native binary |
| `androidMain` | `iosMain` | the two halves of an `expect`/`actual` seam |
| `.aar` | `.framework` | `baseName = "Shared"`, `isStatic = true` in `shared/build.gradle.kts` |
| Kotlin → JVM bytecode → ART (JIT/AOT) | Kotlin → **LLVM IR → native machine code** | no VM on iOS; Kotlin objects are ARC-managed Obj-C objects |
| `implementation` / `api` | `api` **+** `export(...)` | `api` gets the code in; `export` gets the *types into the Obj-C header* |
| R8 / ProGuard | — | dead code is stripped at link time; no keep rules |
| Jetpack Compose | SwiftUI | |
| `@Composable fun Foo()` | `struct Foo: View { var body }` | |
| `remember { mutableStateOf() }` | `@State` | |
| `ViewModel` held by `viewModelStore` | `@StateObject` holding an `ObservableObject` | |
| `collectAsStateWithLifecycle()` | **nothing** — hand-rolled | see §2 |
| `koinViewModel()` | `KoinIosKt.homeViewModel()` | Koin's reified `get<T>()` can't cross the boundary |
| `Application.onCreate` → `startKoin` | `iOSApp.init()` → `KoinIosKt.doInitKoin()` | |
| `Context` | **nothing** | this absence *is* the DB seam |
| `assets/` | `NSBundle.mainBundle` | `ExerciseAssetReader.ios.kt` |
| `LazyColumn` | `List` / `LazyVStack` in `ScrollView` | |
| `LazyVerticalGrid(Fixed(2))` | `LazyVGrid(columns: [.flexible(), .flexible()])` | no per-item span on iOS |
| `NavHost` + `@Serializable` routes | `NavigationStack` + `.navigationDestination` | navigation is **not** shared |
| `MaterialTheme` / design tokens | hand-ported `ObsidianTheme.swift` | tokens are Compose types; not shared on purpose |
| `Int` (32-bit) | `Int32` | **not** Swift's native `Int` |
| `Long` | `Int64` | |
| `Int?` | `KotlinInt?` | boxed; `Int(truncating:)` to unwrap |
| `List<T>` | `[T]` | generics survive for collections |
| `Set<Long>` | `NSSet` of `KotlinLong` | `contains(int64)` silently fails |
| `Map<String, Double>` | `NSDictionary` of `KotlinDouble` | |
| `enum class` | **class** with singleton class-properties | Swift `switch` is *not* exhaustive |
| `sealed interface` | Obj-C **protocol** + flattened classes | `Widget.RaceGoalWidget` → `WidgetRaceGoalWidget` |
| exhaustive `when` | `as?` cast ladder | no compiler check |
| `Flow<T>` | **nothing** — callback bridge | see §2 |
| top-level `fun foo()` in `Bar.kt` | `BarKt.foo()` | file facade |

---

## 2. The things with no equivalent — and what we did instead

These are the interesting half. Each absence in the platform shows up as a concrete file in this repo.

**No `collectAsStateWithLifecycle()`.** Kotlin/Native doesn't export `Flow` in a form Swift can
observe. `core/common/src/iosMain/kotlin/com/mindset/FlowObserver.kt` is the replacement: it launches
a collector on `Dispatchers.Main` and pushes each emission to a Swift closure, returning a
`FlowSubscription` the Swift view cancels. Three decisions in ~10 lines:

- its **own** `CoroutineScope`, not `viewModelScope` — the *Swift view* owns this observation's lifetime;
- **`Dispatchers.Main`**, because SwiftUI `@Published` writes must happen on the main thread;
- **`Flow<*>` → `(Any?)`**, because generics erase at the Obj-C boundary, so Swift casts once at the call site.

**Nobody calls `onCleared()`.** On Android the `viewModelStore` clears the ViewModel. On iOS nothing
does. If `deinit` doesn't `cancel()` the subscription, the collector and its
`SharingStarted.WhileSubscribed(5_000)` upstream run for the life of the process —
`ProfileViewModel` even has an infinite midnight-ticker flow that would never stop.

**No `Context`.** That's the entire justification for the DB seam:
`core/database/src/iosMain/.../DatabaseBuilder.ios.kt` gets a file path from
`NSFileManager`'s Documents directory instead, and uses `BundledSQLiteDriver` because Kotlin/Native
has no framework SQLite. The *repository* above it is pure `commonMain` — the seam is drawn at the
narrowest place where the OS actually differs. That's the rule: platform code only for OS-governed
capabilities (DB driver, health APIs, scheduling, notifications, secure storage).

**No shared theme.** `:core:designsystem` is a plain Android library. The hex values are the
contract; `ObsidianTheme.swift` restates them in SwiftUI primitives. Sharing them would mean pulling
Compose Multiplatform's UI layer into the iOS framework just to hold constants.

**No shared navigation.** Android uses type-safe Navigation Compose routes; iOS uses `TabView` +
`NavigationStack`. Deliberate — navigation is the most platform-idiomatic part of an app.

---

## 3. Two ways to consume shared state from Swift

The port uses both, on purpose, and the contrast is the point.

**Direct** — `StationsStore.swift` publishes the Kotlin `StationsUiState` straight through. Safe
because `StationCardUi` is flat: pre-formatted `String`s and one enum. No `Map`, no `Set`, no
`Instant`. Least code.

**Anti-corruption layer** — `HomeStore.swift` converts to Swift-native types at the boundary.
`HomeUiState` is a sealed interface nested inside a sealed interface, plus a `Set<Long>`, two `Map`s,
and a raw `Session` carrying `kotlin.time.Instant`. Passing that into SwiftUI would push three
problems into every view:

1. **No exhaustiveness.** Sealed types export as protocols/classes, so a 6th widget renders blank at
   runtime instead of failing the build. Kotlin enums have the *same* problem — a Kotlin `enum class`
   does **not** become a Swift `enum`, so even switching on `WidgetType` needs a `default`.
2. **Boxing.** `Set<Long>` → `NSSet` of `KotlinLong`; `set.contains(someInt64)` returns false forever.
3. **Width.** Kotlin `Int` is 32-bit and arrives as `Int32`.

Converting once turns all three into Swift enums and value types, after which the compiler does its
normal job — `HomeView`'s `switch` has no `default` and a Kotlin shape change breaks exactly one file.

**When to choose which:** flat, pre-formatted state → direct. Nested, boxed, or domain-leaking state
→ convert. The real fix for Home is upstream — flatten the hierarchy, or expose epoch millis instead
of `Instant` so the domain type never crosses at all — but that's a Kotlin change affecting Android,
so it's a design conversation, not a port detail.

---

## 4. What the dev loop actually looks like

1. Edit `commonMain` in Android Studio.
2. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — fastest way to surface **Kotlin** errors.
   `iosMain` code only compiles when an iOS target builds, which is exactly how `KoinIos.kt` sat
   broken (referencing a deleted `StatsViewModel`) while every Android build stayed green.
3. Build in Xcode. The project has a Run Script phase calling
   `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so Xcode rebuilds and embeds the framework
   itself. **Swift errors only appear after the framework regenerates** — a Kotlin rename shows up as
   a Swift error one build later.
4. When Swift and Kotlin disagree about a name or a nullability, read the generated Objective-C header:
   `shared/build/.../Shared.framework/Headers/Shared.h`. That header is ground truth for what Swift
   sees — flattened nested names, boxed types, nullability annotations, enum spellings.

Two Xcode facts worth knowing: the target uses a `PBXFileSystemSynchronizedRootGroup`, so every
`.swift` on disk is compiled (deleting a file *is* the whole edit — no `project.pbxproj` surgery).
And `Config.xcconfig` holds `PRODUCT_BUNDLE_IDENTIFIER` / `TEAM_ID`.

---

## 5. Question bank

Things this codebase lets you answer from experience rather than theory.

- **Why `expect`/`actual` for the DB builder but not the repository?** Because only the *builder*
  touches an OS-governed capability (a file path / `Context`). Draw the seam at the narrowest point;
  everything above it stays in `commonMain` and gets tested once.
- **Why `isStatic = true`?** Static linking avoids shipping a dynamic framework and the associated
  load-time cost; the trade is binary size and no runtime sharing between consumers.
- **Why is `export(...)` needed on top of `api(...)`?** `api` puts the module on the compile
  classpath; `export` is what puts its *types* into the generated Obj-C header. Without it Swift
  can't name `HomeUiState` even though `HomeViewModel` returns it.
- **Why is it `doInitKoin()` and not `initKoin()`?** Kotlin/Native mangles Obj-C selectors in the
  ARC `init`/`new` method families. Naming it explicitly keeps Kotlin and Swift in sync instead of
  letting the compiler pick a surprising name.
- **Hand-rolled bridge vs SKIE / KMP-NativeCoroutines?** SKIE would give `Flow` → `AsyncSequence`,
  real Swift enums for sealed classes, and default-argument overloads — removing most of §3's pain.
  The cost is a compiler plugin in the build and a dependency on its release cadence. Hand-rolling is
  ~10 lines and makes the boundary explicit; I'd adopt SKIE once more than a couple of screens are
  paying the cast-ladder tax.
- **What does `@Immutable` in `commonMain` cost iOS?** Nothing. Compose **runtime** is multiplatform
  and is already a `commonMain` dependency in these modules (`org.jetbrains.compose.runtime`, not the
  androidx artifact); Compose **UI** is not involved. It's a stability hint the Compose compiler uses
  on Android and iOS ignores.
- **Why is the UI never allowed to read the network?** Offline-first: the DB is the single source of
  truth, the UI observes it via `Flow`, and the network only feeds the DB (pull) and drains the
  outbox (push). This is why the iOS app works on a plane with no code changes.
- **What's still not ported, and why?** Log Workout (11 intents, 4 StateFlows, a 3-deep nested list,
  a 6-case sealed `CaptureFields`, 12 boxed nullables on the keystroke path) and History
  (`Flow<PagingData<Session>>` — Paging 3 has no Swift consumer at all and needs a dedicated
  non-paged seam). Knowing *why* a thing is hard to port is a better answer than having ported it.
