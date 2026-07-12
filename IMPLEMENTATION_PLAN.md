# Option B Implementation Plan — Vico/Compose Graph Migration

## How to Use This File

> **Single source of truth for the Juggluco graph migration.**
>
> - **At the start of every session**, the coding agent (BOB) **must read this file** in full to
>   restore context: know which phases are in progress, which tasks are done, and what was last
>   touched.
> - **During a session**, update checkbox states (`[ ]` → `[x]`) as individual tasks are completed.
>   Update the `Status:` line of a phase when it transitions (`Not Started` → `In Progress` →
>   `Complete`).
> - **At the end of every session**, append a new row to the [Session Log](#session-log) table with
>   today's date, a one-sentence summary of what was accomplished, and which phase numbers were
>   touched.
> - **After every session**, stage and commit this file so the updated state is persisted in git:
>   ```
>   git add IMPLEMENTATION_PLAN.md
>   git commit -m "docs: update implementation plan — session <date>"
>   ```

---

## 1. Project Goal Summary

Option B retires the entire C++/OpenGL ES 2/NanoVG/JNI rendering stack that currently drives
the glucose curve and replaces it with a modern, fully Kotlin, Jetpack Compose + Vico chart
hosted in the `mobile` product-flavor only. The new chart is data-driven by a pure-Kotlin
`GraphDataRepository` interface that mirrors the current native data contracts, delivers
Material 3 / Material You theming, supports both portrait and landscape orientations natively
(eliminating the hard-coded landscape lock), and ships with full gesture support (pinch-zoom,
scroll/fling, long-press on data points), while the WearOS variant retains the existing
OpenGL path unchanged until a separate Compose-for-Wear phase is planned.

---

## 2. Architecture Overview

### Current Architecture

```
Java/Kotlin (host)                   C++ native layer
──────────────────                   ─────────────────────────────────────────────
GlucoseCurve.java                    javacurve.cpp  ← JNI bridge
  extends GLSurfaceView               ├── curve.cpp + JCurve.hpp  (main renderer)
    │                                 ├── appcurve.cpp            (app-level wrapper)
    ├── MyRenderer.java               ├── percentile.cpp          (statistics / shading)
    │     onDrawFrame → Natives.step()├── shownums.cpp            (data-point overlays)
    │     onSurfaceChanged → Natives.resize()
    │                                 └── nanovg/  (NanoVG GLES2 vector graphics lib)
    └── Touch gestures → Natives.translate/xscale/flingX/longpress

Layout.java (custom ViewGroup)
  Menu/dialog overlays added as addContentView() on top of the GLSurfaceView
```

**Problems with the current architecture:**
- All rendering logic is in C++ — difficult to iterate on visual style.
- NanoVG is unmaintained (last commit 2019); no Material You / dark-mode integration.
- Portrait support requires C++ layout branching; partially started but incomplete.
- 17-language font atlas managed manually in C++ (`scriptFonts.cpp`).
- Orientation locked to landscape via a C++ integer fed to `setRequestedOrientation()`.

### Target Architecture

```
Kotlin / Compose (mobile flavor only)
──────────────────────────────────────────────────────────────
MainActivity
  └── ComposeView (replaces GlucoseCurve/GLSurfaceView)
        └── GlucoseChartScreen  (@Composable)
              ├── Vico CartesianChartHost
              │     ├── LineLayer  (glucose time-series, per-sensor colours)
              │     ├── ThresholdLines  (target range bands)
              │     └── CustomMarker  (long-press data-point info)
              └── ChartOverlays  (percentile band, statistics panel)

GraphDataRepository  (Kotlin interface)
  └── NativeGraphDataRepository  (implements interface via JNI during transition)
  └── PureKotlinGraphDataRepository  (final implementation, no native code)

GlucoseChartViewModel  (ViewModel + StateFlow)
  └── consumes GraphDataRepository
  └── exposes CartesianChartModel to the Composable

WearOS variant  → unchanged (retains GLSurfaceView + NanoVG)
```

---

## 3. Phased Implementation Plan

---

### Phase 1 — Audit & Freeze

**Objective:** Produce a complete, written inventory of every native call site, data-feed path,
touch-event path, and settings sync call so that nothing is missed during the migration; then
freeze the C++ layer against further changes.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] List every `Natives.*` call in `GlucoseCurve.java` and `MyRenderer.java` in a comment block
      or companion doc section, tagged by category (render / data / gesture / settings / lifecycle).
- [ ] Identify the complete data flow: what C++ structs hold glucose data, how
      `Natives.step()` reads them, and what the minimal set of fields is that a Kotlin replacement
      must receive.
- [ ] Document every touch-gesture → native mapping:
      `translate`, `xscale`, `flingX`, `longpress`, `isbutton`, `prevday`, `nextday`.
- [ ] Identify all settings values read by the renderer at runtime
      (`getScreenOrientation`, `getInvertColors`, `getshowscans`, `getsystemUI`, etc.).
- [ ] Identify every JNI callback from C++ → Java (`summaryready`, `showsensorinfo`,
      `glucosecurve` object reference, etc.).
- [ ] Add a `// MIGRATION-FREEZE` comment header to `javacurve.cpp` and `curve.cpp` signalling
      that these files must not be modified during the migration.
- [ ] Create `docs/jni_inventory.md` with the full audit results.

---

### Phase 2 — Data Layer Abstraction

**Objective:** Introduce a pure-Kotlin `GraphDataRepository` interface that mirrors the current
native data contracts, completely decoupled from JNI, so the Vico chart can be built against it
without touching native code.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] Create `GraphDataRepository.kt` interface in `Common/src/mobile/java/tk/glucodata/chart/`
      with methods mirroring identified native contracts:
      `fun observeGlucoseEntries(): Flow<List<GlucoseEntry>>`,
      `fun observeViewport(): Flow<ChartViewport>`,
      `fun observeSettings(): Flow<ChartSettings>`.
- [ ] Define Kotlin data classes: `GlucoseEntry(timestampMs: Long, valueMgdL: Float, sensorIndex: Int)`,
      `ChartViewport(startMs: Long, endMs: Long, minY: Float, maxY: Float)`,
      `ChartSettings(targetLow: Float, targetHigh: Float, invertColors: Boolean, unit: GlucoseUnit)`.
- [ ] Implement `NativeGraphDataRepository` that bridges the existing JNI callbacks
      (e.g., updates `StateFlow` from the `@Keep` callback methods currently on `GlucoseCurve`)
      as the transitional implementation.
- [ ] Write unit tests for `GraphDataRepository` contract using a `FakeGraphDataRepository`
      with deterministic test data.
- [ ] Create `GlucoseChartViewModel` that consumes `GraphDataRepository` and exposes
      `StateFlow<CartesianChartModel>` (Vico model type) to the Compose layer.
- [ ] Write unit tests for `GlucoseChartViewModel` mapping logic.

---

### Phase 3 — Vico Integration (Non-Destructive)

**Objective:** Add Vico to the `mobile` flavor and build a fully functional `GlucoseChartScreen`
composable running side-by-side with the existing `GLSurfaceView` behind a feature flag —
so both can be tested without removing anything.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] Un-comment `id 'org.jetbrains.kotlin.android'` and add
      `id 'org.jetbrains.kotlin.plugin.compose'` in `Common/build.gradle`.
- [ ] Add `buildFeatures { compose = true }` and `composeOptions` to the `android` block in
      `Common/build.gradle`.
- [ ] Add Compose BOM and Vico dependencies under `mobileImplementation` only:
      ```
      mobileImplementation platform('androidx.compose:compose-bom:2024.xx.xx')
      mobileImplementation 'androidx.compose.ui:ui'
      mobileImplementation 'androidx.compose.material3:material3'
      mobileImplementation 'androidx.activity:activity-compose:...'
      mobileImplementation 'com.patrykandpatrick.vico:compose-m3:...'
      mobileImplementation 'com.patrykandpatrick.vico:core:...'
      ```
- [ ] Create `GlucoseChartScreen.kt` — a `@Composable` wrapping
      `CartesianChartHost` with a `LineLayer`, connected to `GlucoseChartViewModel`.
- [ ] Implement target-range horizontal threshold band using Vico `ThresholdLine` or a
      custom `CartesianLayer` decorator.
- [ ] Implement a custom Vico `Marker` that fires a callback on long-press over a data point,
      replicating the `Natives.longpress(x, y)` contract so the existing number-entry dialog
      can still be opened.
- [ ] Implement pinch-zoom and scroll/fling gestures via Vico's built-in
      `rememberVicoZoomState` / `rememberVicoScrollState`.
- [ ] Add a `USE_VICO_CHART` boolean feature flag in `Applic.java` (or a Kotlin companion object)
      defaulting to `false`.
- [ ] In `MainActivity.startdisplay()`, branch on the flag: `false` → current
      `GlucoseCurve` path; `true` → `ComposeView` hosting `GlucoseChartScreen`.
- [ ] Enable the flag in a debug build and perform visual parity testing against the
      existing renderer for at least: line rendering, target band, data point markers,
      sensor colour coding, dark/light mode.
- [ ] Document all visual parity gaps discovered in `docs/jni_inventory.md` under a
      "Parity Gaps" section.

---

### Phase 4 — JNI Bridge Removal

**Objective:** Once visual parity is confirmed, flip the feature flag permanently to `true`,
remove all remaining JNI call sites that are no longer needed for charting, and delete the
C++ source tree files that served only the renderer.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] Flip `USE_VICO_CHART` default to `true` and remove the `false` branch from
      `MainActivity.startdisplay()`.
- [ ] Remove `GlucoseCurve.java` (the `GLSurfaceView` subclass) after verifying no remaining
      references outside the deleted branch.
- [ ] Remove `MyRenderer.java`.
- [ ] Remove all charting-only `Natives.*` calls and their corresponding JNI declarations in
      `Natives.java`: `step`, `badscan`, `initopengl`, `resize`, `translate`, `xscale`,
      `flingX`, `longpress`, `isbutton`, `prevday`, `nextday`, `openglversion`, `freey`.
- [ ] Remove the corresponding `extern "C" JNIEXPORT` functions from `javacurve.cpp`.
- [ ] Remove `curve.cpp`, `appcurve.cpp`, `percentile.cpp`, `shownums.cpp`,
      `scriptFonts.cpp`, all `*jugglucotext.cpp` files, and the `nanovg/` directory
      **from the mobile build** (keep them in the WearOS CMake paths).
- [ ] Update `CMakeLists.txt` to exclude the deleted files from the `mobile` build targets.
- [ ] Verify the WearOS build still compiles and renders correctly (must be unaffected).
- [ ] Run the full release build for all enabled `mobile` variants and confirm no link errors.

---

### Phase 5 — Portrait Support & Layout

**Objective:** Remove the hard-coded landscape orientation lock, implement fully responsive
layouts for both orientations, and verify on all target screen sizes.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] In the C++ settings struct, change the default value of `screenOrientation` from
      `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` (0) to
      `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED` (-1).
- [ ] Add a user-facing orientation toggle in the Settings screen (Landscape / Portrait / Auto)
      that writes to the same native settings integer.
- [ ] Remove the hard-coded `android:screenOrientation` attribute from `MainActivity` in
      `Common/src/main/AndroidManifest.xml` (it is currently absent there, only in the
      mobile manifest — confirm and remove if present).
- [ ] Create `Common/src/mobile/res/layout-port/menus.xml` (portrait variant) that
      `Menus.show()` already tries to load via `createConfigurationContext`.
- [ ] Audit all `Layout.portraitRows()` / `Layout.portraitRow()` call sites in dialog and
      settings code; ensure stacking works correctly in portrait.
- [ ] In `GlucoseChartScreen.kt`, use `BoxWithConstraints` or `WindowSizeClass` to switch
      between a portrait layout (chart above stats panel) and landscape layout (chart
      left, stats right).
- [ ] Test on: phone portrait, phone landscape, tablet portrait, tablet landscape,
      foldable (if available).
- [ ] Verify edge-to-edge insets (`systembarTop/Bottom/Left/Right`) are applied correctly
      in both orientations.

---

### Phase 6 — Polish & Hardening

**Objective:** Apply Material 3 theming, complete accessibility labelling, profile rendering
performance, and run final QA before the feature flag is removed entirely.

**Status:** `[ ] Not Started`

**Tasks:**

- [ ] Apply the app's existing dynamic theme (`DynamicThemeUtils`) to the Compose chart
      using `MaterialTheme` with `dynamicColorScheme` (Android 12+) and a static fallback.
- [ ] Ensure dark mode / light mode toggling (via `getInvertColors()`) is reflected by
      reacting to `isSystemInDarkTheme()` or the app's existing theme state.
- [ ] Add `contentDescription` / `semantics` to all chart elements for accessibility.
- [ ] Profile the Vico chart with Android Studio's Profiler: target < 16 ms per frame during
      scroll and < 32 ms during initial render of 24 hours of data (~288 points).
- [ ] Verify RTL layout direction works correctly with Vico (Arabic, Hebrew locales).
- [ ] Add Compose UI tests (`composeTestRule`) for: chart renders with data, chart renders
      empty state, long-press opens number-entry dialog.
- [ ] Remove the `USE_VICO_CHART` feature flag and its dead-code branches entirely.
- [ ] Update `README.md` to note that the OpenGL/NanoVG stack has been retired for the
      mobile build.
- [ ] Tag a release candidate and run regression testing against the previous APK.

---

## 4. Session Log

| Date | Session Summary | Phases Touched |
|------|----------------|----------------|
| 2026-07-12 | Initial plan created | — |
