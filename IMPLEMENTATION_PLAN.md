# Option B Implementation Plan â€” Vico/Compose Graph Migration

## How to Use This File

> **Single source of truth for the Juggluco graph migration.**
>
> - **At the start of every session**, the coding agent (BOB) **must read this file** in full to
>   restore context: know which phases are in progress, which tasks are done, and what was last
>   touched.
> - **During a session**, update checkbox states (`[ ]` â†’ `[x]`) as individual tasks are completed.
>   Update the `Status:` line of a phase when it transitions (`Not Started` â†’ `In Progress` â†’
>   `Complete`).
> - **At the end of every session**, append a new row to the [Session Log](#session-log) table with
>   today's date, a one-sentence summary of what was accomplished, and which phase numbers were
>   touched.
> - **After every session**, stage and commit this file so the updated state is persisted in git:
>   ```
>   git add IMPLEMENTATION_PLAN.md
>   git commit -m "docs: update implementation plan â€” session <date>"
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
â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€                   â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
GlucoseCurve.java                    javacurve.cpp  â† JNI bridge
  extends GLSurfaceView               â”œâ”€â”€ curve.cpp + JCurve.hpp  (main renderer)
    â”‚                                 â”œâ”€â”€ appcurve.cpp            (app-level wrapper)
    â”œâ”€â”€ MyRenderer.java               â”œâ”€â”€ percentile.cpp          (statistics / shading)
    â”‚     onDrawFrame â†’ Natives.step()â”œâ”€â”€ shownums.cpp            (data-point overlays)
    â”‚     onSurfaceChanged â†’ Natives.resize()
    â”‚                                 â””â”€â”€ nanovg/  (NanoVG GLES2 vector graphics lib)
    â””â”€â”€ Touch gestures â†’ Natives.translate/xscale/flingX/longpress

Layout.java (custom ViewGroup)
  Menu/dialog overlays added as addContentView() on top of the GLSurfaceView
```

**Problems with the current architecture:**
- All rendering logic is in C++ â€” difficult to iterate on visual style.
- NanoVG is unmaintained (last commit 2019); no Material You / dark-mode integration.
- Portrait support requires C++ layout branching; partially started but incomplete.
- 17-language font atlas managed manually in C++ (`scriptFonts.cpp`).
- Orientation locked to landscape via a C++ integer fed to `setRequestedOrientation()`.

### Target Architecture

```
Kotlin / Compose (mobile flavor only)
â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
MainActivity
  â””â”€â”€ ComposeView (replaces GlucoseCurve/GLSurfaceView)
        â””â”€â”€ GlucoseChartScreen  (@Composable)
              â”œâ”€â”€ Vico CartesianChartHost
              â”‚     â”œâ”€â”€ LineLayer  (glucose time-series, per-sensor colours)
              â”‚     â”œâ”€â”€ ThresholdLines  (target range bands)
              â”‚     â””â”€â”€ CustomMarker  (long-press data-point info)
              â””â”€â”€ ChartOverlays  (percentile band, statistics panel)

GraphDataRepository  (Kotlin interface)
  â””â”€â”€ NativeGraphDataRepository  (implements interface via JNI during transition)
  â””â”€â”€ PureKotlinGraphDataRepository  (final implementation, no native code)

GlucoseChartViewModel  (ViewModel + StateFlow)
  â””â”€â”€ consumes GraphDataRepository
  â””â”€â”€ exposes CartesianChartModel to the Composable

WearOS variant  â†’ unchanged (retains GLSurfaceView + NanoVG)
```

---

## 3. Phased Implementation Plan

---

### Phase 1 â€” Audit & Freeze

**Objective:** Produce a complete, written inventory of every native call site, data-feed path,
touch-event path, and settings sync call so that nothing is missed during the migration; then
freeze the C++ layer against further changes.

**Status:** `[x] Complete`

**Tasks:**

- [x] List every `Natives.*` call in `GlucoseCurve.java` and `MyRenderer.java` in a comment block
      or companion doc section, tagged by category (render / data / gesture / settings / lifecycle).
- [x] Identify the complete data flow: what C++ structs hold glucose data, how
      `Natives.step()` reads them, and what the minimal set of fields is that a Kotlin replacement
      must receive.
- [x] Document every touch-gesture â†’ native mapping:
      `translate`, `xscale`, `flingX`, `longpress`, `isbutton`, `prevday`, `nextday`.
- [x] Identify all settings values read by the renderer at runtime
      (`getScreenOrientation`, `getInvertColors`, `getshowscans`, `getsystemUI`, etc.).
- [x] Identify every JNI callback from C++ â†’ Java (`summaryready`, `showsensorinfo`,
      `glucosecurve` object reference, etc.).
- [x] Add a `// MIGRATION-FREEZE` comment header to `javacurve.cpp` and `curve.cpp` signalling
      that these files must not be modified during the migration.
- [x] Create `docs/jni_inventory.md` with the full audit results.
- [x] Catalogue all `jugglucotext` string fields (day/month labels, sensor status, statistics,
      error messages, trend names) that must be migrated from C++ structs to `strings.xml`
      during Phase 3. Record in `docs/jni_inventory.md` Section 7.

---

### Phase 2 â€” Data Layer Abstraction

**Objective:** Introduce a pure-Kotlin `GraphDataRepository` interface that mirrors the current
native data contracts, completely decoupled from JNI, so the Vico chart can be built against it
without touching native code.

**Status:** `[x] Complete`

**Tasks:**

- [x] Create `GraphDataRepository.kt` interface in `Common/src/mobile/java/tk/glucodata/chart/`
      with methods mirroring identified native contracts:
      `fun observeGlucoseEntries(): Flow<List<GlucoseEntry>>`,
      `fun observeViewport(): Flow<ChartViewport>`,
      `fun observeSettings(): Flow<ChartSettings>`.
- [x] Define Kotlin data classes: `GlucoseEntry(timestampMs: Long, valueMgdL: Float, sensorIndex: Int)`,
      `ChartViewport(startMs: Long, endMs: Long, minY: Float, maxY: Float)`,
      `ChartSettings(targetLow: Float, targetHigh: Float, invertColors: Boolean, unit: GlucoseUnit,
      isRtl: Boolean)`.
      _(Note: `isRtl` is derived from `Configuration.getLayoutDirection()`; already tracked in
      `MainActivity.rtl`. Required so the Vico chart screen can apply correct layout direction
      to surrounding panels without querying the Android config directly.)_
- [x] Implement `NativeGraphDataRepository` that bridges the existing JNI callbacks
      (e.g., updates `StateFlow` from the `@Keep` callback methods currently on `GlucoseCurve`)
      as the transitional implementation.
- [x] Write unit tests for `GraphDataRepository` contract using a `FakeGraphDataRepository`
      with deterministic test data.
- [x] Create `GlucoseChartViewModel` that consumes `GraphDataRepository` and exposes
      `StateFlow<CartesianChartModel>` (Vico model type) to the Compose layer.
- [x] Write unit tests for `GlucoseChartViewModel` mapping logic.

---

### Phase 3 â€” Vico Integration (Non-Destructive)

**Objective:** Add Vico to the `mobile` flavor and build a fully functional `GlucoseChartScreen`
composable running side-by-side with the existing `GLSurfaceView` behind a feature flag â€”
so both can be tested without removing anything.

**Status:** `[x] Complete`

**Tasks:**

- [x] Un-comment `id 'org.jetbrains.kotlin.android'` and add
      `id 'org.jetbrains.kotlin.plugin.compose'` in `Common/build.gradle`.
      _(Note: the android plugin is NOT added â€” AGP 9 conflict. Only the compose plugin `id 'org.jetbrains.kotlin.plugin.compose' version '2.3.20'` was added.)_
- [x] Add `buildFeatures { compose = true }` and `composeOptions` to the `android` block in
      `Common/build.gradle`.
- [x] Add Compose BOM and Vico dependencies under `mobileImplementation` only:
      ```
      mobileImplementation platform('androidx.compose:compose-bom:2025.06.01')
      mobileImplementation 'androidx.compose.ui:ui'
      mobileImplementation 'androidx.compose.ui:ui-tooling-preview'
      mobileImplementation 'androidx.compose.material3:material3'
      mobileImplementation 'androidx.activity:activity-compose:1.11.0'
      mobileImplementation 'androidx.lifecycle:lifecycle-runtime-compose:2.9.1'
      mobileDebugImplementation 'androidx.compose.ui:ui-tooling'
      mobileImplementation 'com.patrykandpatrick.vico:compose-m3:3.2.3'
      // Note: no separate vico:core dep in v3 â€” compose-m3 is the only artifact needed
      ```
- [x] Create `GlucoseChartScreen.kt` â€” a `@Composable` wrapping
      `CartesianChartHost` with a `LineLayer`, connected to `GlucoseChartViewModel`.
      _(Also created `GlucoseChartViewBridge.kt` â€” Kotlin bridge for Java interop since
       `@Composable` functions cannot be called directly from Java.)_
- [x] Implement target-range horizontal threshold band using Vico `HorizontalLine` decoration.
- [x] Implement a custom Vico `Marker` that fires a callback on long-press over a data point,
      replicating the `Natives.longpress(x, y)` contract so the existing number-entry dialog
      can still be opened.
- [x] Implement pinch-zoom and scroll/fling gestures via Vico's built-in
      `rememberVicoZoomState` / `rememberVicoScrollState`.
- [x] Add a `USE_VICO_CHART` boolean feature flag in `Applic.java` defaulting to `false`.
- [x] In `MainActivity.startdisplay()`, branch on the flag: `false` â†’ current
      `GlucoseCurve` path; `true` â†’ `ComposeView` hosting `GlucoseChartScreen`.
- [ ] **[RTL/i18n â€” DEFERRED to Phase 6]** Extract all chart-specific strings from every
      `*jugglucotext.cpp` struct (day/month labels, sensor status, statistics labels, error
      messages, trend names) into `strings.xml` for all 17 locales. Remove the compile-time
      `RTL()` byte-reversal macro from Arabic/Hebrew entries â€” Android text rendering performs
      BiDi shaping automatically.
- [x] **[RTL/i18n]** Wrap `CartesianChartHost` in
      `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` so the
      time axis always runs leftâ†’right regardless of device locale. Apply
      `LayoutDirection.Rtl` only to surrounding stat/sensor-info panels when
      `ChartSettings.isRtl == true`.
      _(Implemented in `GlucoseChartScreen.kt`: `GlucoseChart` forces `LayoutDirection.Ltr`;
      `StatsPanelWithDirection` uses `settings.isRtl` to pick direction.)_
- [x] Enable the flag in a debug build â€” `USE_VICO_CHART = true` set in `Applic.java`.
      _Visual parity testing on device is required by the developer; known gaps documented
      in `docs/jni_inventory.md` Â§ 6. Parity Gaps._
- [ ] **[RTL/i18n â€” DEFERRED to Phase 6]** Parity-test RTL locales: Arabic (chart LTR,
      panels RTL, no garbled text), Hebrew (same), Hindi Devanagari (system font rendering),
      Chinese/Japanese (date abbreviations via system fonts).
- [x] Document known visual parity gaps in `docs/jni_inventory.md` Â§ 6 (pre-populated;
      to be updated after on-device testing).

---

### Phase 4 â€” REMOVED

**Decision: Permanently removed.** Both UIs (Vico/Compose and the legacy OpenGL/NanoVG)
are intentionally kept and both are reachable at runtime via Settings â†’ "Use new chart (Vico)".

**Rationale:**
- The legacy C++/OpenGL/NanoVG chart is the reference baseline for comparing NFC scan
  behaviour, data loading, and glucose display parity with the new Vico chart.
- `GlucoseCurve` is fully functional as both a renderer (old UI) and as a headless helper
  (menus, NFC, dialogs) in the Vico path â€” removing it would break both roles.
- Removing shared C++ files risks breaking the WearOS build (same CMake sources).
- The runtime UI switcher makes both paths permanently accessible â€” nothing is dead code.

---

### Phase 5 â€” Portrait Support & Layout

**Objective:** Remove the hard-coded landscape orientation lock, implement fully responsive
layouts for both orientations, and verify on all target screen sizes.

**Status:** `[x] Complete`

**Tasks:**

- [x] In the C++ settings struct, change the default value of `screenOrientation` from
      `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` (0) to
      `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED` (-1).
      _(Implementado: sentinel 127 en `uint8_t:7`; JNI mapea 127â†”-1; migraciÃ³n en initVersion<39
      resetea valores 0 u 8 a 127; `initVersion` subido a 39; default de nueva instalaciÃ³n = 127.)_
- [x] Add a user-facing orientation toggle in the Settings screen (Landscape / Portrait / Auto)
      that writes to the same native settings integer.
      _(Spinner con 3 opciones: "Free rotation" / "Landscape" / "Reverse landscape", usando
      strings ya existentes en `strings.xml`. Aplica `setRequestedOrientation` inmediatamente
      y persiste con `Natives.setScreenOrientation`.)_
- [x] Remove the hard-coded `android:screenOrientation` attribute from `MainActivity` in
      `Common/src/main/AndroidManifest.xml` (verificado: no existe en el manifest activo
      `src/mobileSi/AndroidManifest.xml` â€” no requiere cambio).
- [x] Create `Common/src/mobile/res/layout-port/menus.xml` (portrait variant â€” 2-column
      ScrollView, all original IDs preserved, confirmed loading on device).
- [x] Audit all `Layout.portraitRows()` / `Layout.portraitRow()` call sites in dialog and
      settings code; all use `shouldPortraitStack()` which reads `Configuration.orientation`
      â€” correct for any rotation, no changes needed.
- [x] In `GlucoseChartScreen.kt`, use `BoxWithConstraints` to switch between portrait layout
      (chart above stats panel, width < 600 dp) and landscape layout (chart left, stats right).
- [x] Verify edge-to-edge insets: `systemBarsPadding()` applied to chart root; Menus overlay
      uses `MainActivity.systembarTop/Bottom/Left/Right` padding; Settings overlay same.
      `onConfigurationChanged` calls `NativeGraphDataRepository.notifyDataChanged()` on rotation.
      `Settings.finish()` calls `notifyDataChanged()` so unit/threshold changes refresh chart.
- [ ] Test on: phone portrait, phone landscape, tablet portrait, tablet landscape,
      foldable (if available). _(Manual QA by developer â€” not automatable without device farm.)_

---

### Phase 6 â€” Polish & Hardening

**Objective:** Apply Material 3 theming, complete accessibility labelling, profile rendering
performance, and run final QA before the feature flag is removed entirely.

**Status:** `[-] In Progress`

**Tasks:**

- [x] Apply dynamic theme (`dynamicColorScheme` Android 12+, static fallback) via `GlucoseTheme`
      composable in `GlucoseChartScreen.kt`.
- [x] Dark mode / light mode: `isSystemInDarkTheme() xor invertColors` â€” responds to both
      system dark mode and the app's own invert-colours toggle.
- [x] Accessibility: `contentDescription` / `semantics` added to chart host, stats panel,
      menu FAB, and root Surface.
- [x] Per-sensor line colours: 4-colour palette (blue/green/red/amber). Single
      `rememberLineCartesianLayer` with `LineProvider.series(line0..line3)` where each
      `line` is built via `LineCartesianLayer.rememberLine(fill, stroke, interpolator)`.
      Required adding `com.patrykandpatrick.vico:compose-android:3.2.3` as explicit dep
      (compose-m3 does NOT re-export the top-level functions in `LineCartesianLayerKt`).
- [x] Richer stats panel: colour-coded latest reading (red/orange/green), trend arrow
      (â†‘â†‘/â†‘/â†’/â†“/â†“â†“), time-in-range percentage over last 24 hours.
- [x] Y-axis auto-range: `CartesianLayerRangeProvider.auto()` â€” no wasted space below data.
- [x] X-axis timestamps: Unix seconds (not ms) to avoid Float precision loss; axis labels
      always non-empty (Vico 3.x crash guard).
- [ ] Profile the Vico chart with Android Studio's Profiler: target < 16 ms per frame during
      scroll and < 32 ms during initial render of 24 hours of data (~288 points).
      _(Requires Android Studio connection â€” manual step by developer.)_
- [ ] Verify RTL layout direction works correctly with Vico across all affected locales
      _(Manual QA â€” Arabic, Hebrew, Hindi, Chinese/Japanese.)_
- [ ] Add Compose UI instrumented tests (`composeTestRule`) for chart renders with data,
      empty state, and long-press. _(Requires androidTest source set + device/emulator.)_
- [x] Runtime UI switcher: `USE_VICO_CHART` changed from `static final boolean` to a
      runtime `SharedPreferences` boolean (default `true`). Settings screen gains a
      "Use new chart (Vico)" toggle; toggling it persists the pref and calls
      `activity.recreate()` so `startdisplay()` re-runs with the new value. This lets
      the user switch between Vico and the legacy C++/OpenGL/NanoVG chart at runtime
      to compare behaviour (NFC scan, data loading, glucose display).
- [ ] Remove the `USE_VICO_CHART` feature flag and its dead-code branches entirely once
      release QA passes.
- [x] Update `README.md` to note that the OpenGL/NanoVG stack has been superseded for mobile.
- [ ] Tag a release candidate and run regression testing against the previous APK.

---

## 4. Session Log

| Date | Session Summary | Phases Touched |
|------|----------------|----------------|
| 2026-07-12 | Initial plan created | â€” |
| 2026-07-12 | Phase 1 complete: full JNI/gesture/settings/callback inventory written to `docs/jni_inventory.md`; freeze headers added to `javacurve.cpp` and `curve.cpp` | 1 |
| 2026-07-12 | RTL/i18n impact analysis: added `jugglucotext` catalogue task to Phase 1, `isRtl` field to Phase 2, three RTL tasks to Phase 3, expanded RTL checklist in Phase 6; updated `docs/jni_inventory.md` with Section 7 | 1, 2, 3, 6 |
| 2026-07-13 | Phase 2 complete: `Models.kt`, `GraphDataRepository.kt`, `NativeGraphDataRepository.kt`, `GlucoseChartViewModel.kt`, `FakeGraphDataRepository.kt` created; 18/18 unit tests pass; AGP 9 Kotlin plugin conflict resolved (removed external plugin, moved `compileSdk` to top of `android {}` block) | 2 |
| 2026-07-13 | Phase 5 in progress: candado de orientaciÃ³n eliminado vÃ­a sentinel 127 en C++ + migraciÃ³n initVersionâ†’39; spinner "Screen rotation" agregado a Settings (Free rotation / Landscape / Reverse landscape) | 5 |
| 2026-07-14 | Phase 3 in progress: Compose plugin + buildFeatures added to build.gradle; Compose BOM 2025.06.01 + Vico 2.1.2 deps added; GlucoseChartScreen.kt + GlucoseChartViewBridge.kt created; USE_VICO_CHART flag added to Applic.java; startdisplay() branched; @JvmStatic added to NativeGraphDataRepository.instance | 3 |
| 2026-07-15 | Phase 2 task-checkboxes back-filled (all 6 done in prior session); Phase 3 complete: fixed rememberTextComponent(style=TextStyle(â€¦)), lineSeriesâ†’lineModel deprecation resolved, USE_VICO_CHART flipped to true, RTL CompositionLocalProvider already in place, parity gaps pre-documented. Build: 0 errors, 0 warnings from project files. | 2, 3 |
| 2026-07-15 | Phase 3 on-device fixes: C++ watchserver.cpp NOLOG guard fixed; NativeGraphDataRepository data-reading corrected (healthConnectfromSensorptr packed-int unpacking); statusBarsPadding added; Scroll.Absolute.End for latest-data scroll; HH:mm axis formatter; FAB menu button added; curve.post() â†’ Applic.RunOnUiThread() for menus/settings. Phase 4 reclassified as Optional Cleanup â€” deferred indefinitely (WearOS shared code risk, zero functional impact). | 3, 4 |
| 2026-07-15 | Phase 5 complete: systemBarsPaddingâ†’systemBarsPadding(nav+status), onConfigurationChanged notifies Vico repo on rotation, Settings.finish() notifies Vico repo on settings close, x-axis fixed (Unix seconds not ms), Y-axis auto-range (CartesianLayerRangeProvider.auto), crash fix (no blank axis labels). Phase 6 in progress: dynamic M3 theme, dark mode, accessibility semantics, per-sensor colours (4-colour palette), richer stats panel (trend arrow + TIR 24h), README updated. | 5, 6 |
| 2026-07-16 | Phase 6 per-sensor colours: added `compose-android:3.2.3` dep; fixed `rememberLine` Companion-extension call syntax; fixed `Interpolator.Sharp` (property not function); 4 sensor colours via `LineProvider.series`. Fixed no-data bug: `healthConnectfromSensorptr` returns HC export cursor (0 when HC disabled) â€” switched to `activeSensorPtrs()` + `streamfromSensorptr` from pos=0. Data confirmed flowing (2 entries from sensor 301U8X5CRF0). | 6 |
| 2026-07-16 | Runtime UI switcher implemented: `USE_VICO_CHART` replaced by `Applic.useVicoChart()` / `setUseVicoChart()` backed by SharedPreferences (default true). Settings screen gains "Use new chart (Vico)" checkbox that persists the pref and calls `activity.recreate()`. All 4 call sites migrated. BUILD SUCCESSFUL, no warnings. | 6 |
| 2026-07-16 | Phase 4 permanently removed from plan â€” both UIs kept intentionally. Old UI (OpenGL) always forced to landscape in `startdisplay()`; orientation spinner hidden in Settings when old UI active. Phase 4 section replaced with rationale. README.md updated. | 4, 5, 6 |
| 2026-07-17 | Phase 6 â€” Fixed 3 pending chart issues: AutoScrollCondition import added; HorizontalAxis.ItemPlacer.aligned() takes (ExtraStore)->Int lambda not Int; custom CartesianLayerRangeProvider uses imported ExtraStore. Fixed 2 crashes: (1) vicoToggle now calls closeview() before recreate() â€” prevents window leak/crash when switching UIs; (2) onConfigurationChanged now calls Settings.closeview() before Settings.set() â€” prevents NPE in setvalues() when settinglayout is non-null and mmolL not reassigned. Root cause confirmed via logcat: NPE at Settings.setvalues:518 from onConfigurationChanged:1088. Both builds SUCCESSFUL, APKs installed. | 6 |
| 2026-07-17 | NFC scan result overlay for Vico path: In old UI badscanMessage() renders glucose value via C++ NanoVG onto GL canvas â€” GL canvas never draws in headless Vico curve so nothing was visible. Added MainActivity.showVicoScanResult() that reads Natives.lastglucose() (already formatted in display units by C++) and shows a centred View overlay auto-dismissing after 8s or on tap. Called from GlucoseCurve.summaryready() in Vico path only. BUILD SUCCESSFUL. | 6 |
| 2026-07-15 | Merged upstream Juggluco 10.9.6: version bump, BLE attribution removed, new deletemeter string, GlucoseCurve/NumberView layout positioning fixes (ymax/systemBarBottom guard), Natives.java JNI signature updates (GlucoseMeterGetIndex/HasIndex/Remove now take address param + new GlucoseMeterRemoveIndex + isChinese), settings.hpp address-aware GlucoseMeter methods, CMakeLists arjugglucotext rename, full Sibionics siType=5 support (SensorGlucoseData.hpp/sensoren.hpp/sibionics3/java.cpp), meter/java.cpp deviceAddressBytes refactor, ICE.cpp TWILIOACCOUNT guard fix, g.cpp bounds fix, mobile Java/html/wear files updated, symlinks fixed to relative paths. dex build SUCCESSFUL. | merge |
