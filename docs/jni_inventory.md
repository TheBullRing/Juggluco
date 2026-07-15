# JNI Inventory — Phase 1 Audit

> Generated during Phase 1 — Audit & Freeze.  
> This document is the authoritative record of every JNI call site, data-feed
> path, touch-event path, settings sync call, and C++ → Java callback that the
> Vico migration must replicate or safely delete.

---

## 1. `Natives.*` Calls in `GlucoseCurve.java` (by category)

### 1.1 Render — Frame Drive

| Call | Line | Purpose |
|------|------|---------|
| `Natives.step()` | `MyRenderer.java:45` | Main render loop — called every frame from `onDrawFrame`. Returns `stepresult` bitfield. |
| `Natives.badscan(int kind)` | `MyRenderer.java:42` | Render a "bad scan" error overlay. Returns `stepresult`. |
| `Natives.initopengl(float,float,float,float)` | `MyRenderer.java:61` | One-time GL surface init: passes `smallfontsize`, `menufontsize`, screen density, `headfontsize`. |
| `Natives.resize(int,int,int)` | `MyRenderer.java:54` | Called on every `onSurfaceChanged`: passes pixel width, height, `initscreenwidth`. |
| `Natives.openglversion()` | `MainActivity.java:276` | Returns required OpenGL ES version (2 or 3). Used to configure `GLSurfaceView`. |

**Migration note:** All five are eliminated when `GLSurfaceView` is removed. Vico handles its own rendering loop.

---

### 1.2 Gesture — Touch → Native

| Call | Location | Purpose |
|------|----------|---------|
| `Natives.translate(dx,dy,yold,y)` | `GlucoseCurve.java:596` | Scroll/pan the chart on drag. Returns non-zero if chart changed. |
| `Natives.mouseScale(dx,xold,x)` | `GlucoseCurve.java:592` | Mouse ctrl+drag zoom. |
| `Natives.xscale(scalex,midx)` | `GlucoseCurve.java:422` | Pinch-zoom on the time axis. |
| `Natives.flingX(velocityX)` | `GlucoseCurve.java:676` | Fling gesture — time-axis momentum. |
| `Natives.isbutton(x,y)` | `GlucoseCurve.java:383` | Hit-test the "back/step" overlay button. |
| `Natives.tap(x,y)` | `GlucoseCurve.java:471` | Single-tap: opens sub-menus (floating, settings, date pick). Returns action code. |
| `Natives.longpress(x,y)` | `GlucoseCurve.java:625` | Long-press: returns `hitptr` for the nearest data point, or `0`. |
| `Natives.prevday(int)` | `GlucoseCurve.java:618` | Long-press on left edge — navigate backwards by N days. |
| `Natives.nextday(int)` | `GlucoseCurve.java:622` | Long-press on right edge — navigate forwards by N days. |
| `Natives.prevscr()` | _(gesture in old code)_ | Scroll one screen backwards. |
| `Natives.nextscr()` | _(gesture in old code)_ | Scroll one screen forwards. |
| `Natives.pressedback()` | `javacurve.cpp:505` | Hardware back key pressed. |
| `Natives.movedate(long,int,int,int)` | `GlucoseCurve.java:433` | Jump to a specific calendar date. |
| `Natives.setStartTime(long)` | `javacurve.cpp:637` | Set the visible window start to a specific millisecond. |
| `Natives.settonow()` | `javacurve.cpp:861` | Snap view to "now". |

**Migration note:** `translate`, `xscale`, `flingX` are fully replaced by Vico's built-in
`rememberVicoScrollState` / `rememberVicoZoomState`. `longpress`, `tap`, `prevday`, `nextday`,
`movedate`, `settonow`, `setStartTime` must be re-wired to the `GlucoseChartViewModel` viewport
API. `isbutton` / `pressedback` are removed along with the bad-scan overlay logic.

---

### 1.3 Data Query — Viewport & Bounds

| Call | Purpose |
|------|---------|
| `Natives.getstarttime()` | Returns current viewport start (Unix seconds). Used to seed date-picker. |
| `Natives.getendtime()` | Returns current viewport end. |
| `Natives.oldestdatatime()` | Returns oldest available data point time. Used to limit scroll. |
| `Natives.freey()` | Returns Y-axis free space (used to size overlay layouts). |
| `Natives.numcontrol(w,h)` | Returns column count for the number-list overlay. |
| `Natives.getcolumns(width)` | Returns column count for search results. |

---

### 1.4 Search / Filter

| Call | Purpose |
|------|---------|
| `Natives.search(label,under,above,fromMin,toMin,fwd,regex,amount)` | Execute a data search query. |
| `Natives.stopsearch()` | Clear active search. |
| `Natives.earliersearch()` | Navigate to previous search result. |
| `Natives.latersearch()` | Navigate to next search result. |
| `Natives.getLabels()` | Return list of user-defined labels (for search spinner). |
| `Natives.getmealvar()` | Return the label index that means "meal". |

**Migration note:** Search is UI-overlay logic, not chart rendering. It can remain JNI-driven in
Phase 3; it is isolated from the rendering replacement.

---

### 1.5 Number List (side panel overlay)

| Call | Purpose |
|------|---------|
| `Natives.firstpage()` | Jump to first page of number list. |
| `Natives.lastpage()` | Jump to last page. |
| `Natives.forwardnumlist()` | Page forward. |
| `Natives.backwardnumlist()` | Page backward. |
| `Natives.endnumlist()` | Close number list. |
| `Natives.staticnum()` | Check if numbers are static (read-only). |

---

### 1.6 Settings Read by Renderer

| Call | Purpose |
|------|---------|
| `Natives.getInvertColors()` | Whether to invert chart colours (dark/light mode override). |
| `Natives.getshowscans()` | Show raw scan data points. |
| `Natives.getshowhistories()` | Show historical (Libre) data. |
| `Natives.getshowstream()` | Show streaming data. |
| `Natives.getshownumbers()` | Show user-entered numbers overlay. |
| `Natives.getsystemUI()` / `getsystemui()` | System UI visibility state. |
| `Natives.getScreenOrientation()` | Stored orientation integer. |
| `Natives.setScreenOrientation(int)` | Write orientation setting. |
| `Natives.getfloatglucose()` | Whether floating glucose window is active. |
| `Natives.staticnum()` | Read-only number mode. |
| `Natives.getUsedSensorName()` | Name of the currently active sensor. |
| `Natives.getCPUarch()` | CPU architecture string (used in "about" text only). |
| `Natives.graphlow()` / `graphhigh()` | Y-axis glucose display range. |
| `Natives.targetlow()` / `targethigh()` | Target range band boundaries. |
| `Natives.getunit()` | Glucose unit (mg/dL = 0, mmol/L = 1). |

---

### 1.7 Statistics / Summary

| Call | Purpose |
|------|---------|
| `Natives.analysedays(int,boolean)` | Trigger statistics calculation for N days. Returns `true` when complete. |
| `Natives.endstats()` | Dismiss the statistics overlay. |
| `Natives.summarygraph(boolean)` | Toggle the summary-graph mode. |
| `Natives.makepercentages()` | Trigger percentile calculation. Returns `true` on success. |
| `Natives.makenumbers()` | Trigger number-overlay recalculation. |
| `Natives.getAnalysedays()` / `getAnalysehistory()` | Read stored analysis params. |
| `Natives.percentileEndtime(int)` | Returns end time for the percentile display window. |

---

### 1.8 Lifecycle

| Call | Location | Purpose |
|------|----------|---------|
| `Natives.setpaused(GlucoseCurve val)` | `Natives.java:319` | Passes a global ref to `GlucoseCurve` into C++ so it can call `requestRender()` and invoke `@Keep` callbacks. |

**Migration note:** This is one of the most critical integration points. The C++ layer holds a
`jobject glucosecurve` global ref and calls three methods on it directly (see Section 2).

---

## 2. C++ → Java Callbacks (called from native threads)

These are resolved at JNI `JNI_OnLoad` time by looking up `GlucoseCurve` by class name.

| C++ function | Java target | Java method | Purpose |
|---|---|---|---|
| `visiblebutton()` | `glucosecurve` object | `GlucoseCurve.summaryready()` | Notifies that the statistics summary is ready; Java makes the summary button visible. |
| `callshowsensorinfo(text,ptr)` | `glucosecurve` object | `GlucoseCurve.showsensorinfo(String,long)` | Shows sensor information dialog. |
| `render()` | `glucosecurve` object | `GLSurfaceView.requestRender()` | Requests a new OpenGL frame from a background thread. |

Additionally, via `JNIApplic` (static methods on `Applic`):

| C++ function | Java target | Java method | Purpose |
|---|---|---|---|
| `telldoglucose(...)` | `JNIApplic` | `Applic.doglucose(...)` | Delivers a new glucose reading to Java (notifications, widgets). |
| `javaUpdateDevices()` | `JNIApplic` | `Applic.updateDevices()` | Notifies that Bluetooth device list changed. |
| `bluetoothEnabled()` | `JNIApplic` | `Applic.bluetoothEnabled()` | Queries Bluetooth state. |
| `speak(text)` | `JNIApplic` | `Applic.speak(String)` | Text-to-speech. |
| `resetWearOS()` | `JNIApplic` | `Applic.resetWearOS()` | Resets WearOS connection. |
| `toGarmin(int)` | `JNIApplic` | `Applic.toGarmin(int)` | Sends data to Garmin. |
| `Garmindeletelast(...)` | `JNIApplic` | `Applic.Garmindeletelast(int,int,int)` | Removes last Garmin entry. |
| `switchbluetooth(...)` | `JNIApplic` | `Applic.switchbluetooth(String,[B,Z)` | Switch Bluetooth device. |
| `bluePermission()` | `JNIApplic` | `Applic.bluePermission()` | Query Bluetooth permission state. |

**Migration note:** The `summaryready` / `showsensorinfo` / `requestRender` callbacks target
`GlucoseCurve` specifically. In the Vico path, `summaryready` becomes a `StateFlow` emission
from `GlucoseChartViewModel`; `showsensorinfo` becomes a `SharedFlow<SensorInfoEvent>` consumed
by a Compose `LaunchedEffect`; `requestRender` is eliminated (Vico recomposes reactively).
The `Applic.*` static callbacks are **not** renderer-specific and must be preserved.

---

## 3. `Natives.setpaused(GlucoseCurve val)` — Lifecycle Contract

```
// Called from MainActivity to wire the C++ layer to this Java surface:
Natives.setpaused(curve);   // on resume / after create
Natives.setpaused(null);    // on pause / destroy (releases GlobalRef)
```

In `GlucoseCurve.onResume()` / `onPause()`:
- `onResume` (inherited from `GLSurfaceView`) → triggers `Applic.setcurve(this)` which calls `setpaused`.
- `onPause` → `Applic.setcurve(null)`.

**Migration:** In the Vico path, `setpaused(null)` must still be called during Activity
pause/destroy to release the GlobalRef and prevent leaks — until the C++ layer is fully removed
in Phase 4.

---

## 4. Complete Data Flow: How Glucose Data Reaches the Renderer

```
[BLE/NFC/Libre sensor]
        │
        ▼
C++ sensor layer (sensoren.hpp / SensorGlucoseData.hpp)
   stores readings as ScanData[] and SensorGlucoseData[] structs
        │
        ▼
JCurve (curve.cpp) reads the structs directly in the render loop via
   appcurve.onestep(genVG)  ←── called from Natives.step() each frame
        │
        ▼
NanoVG draws lines, circles, axes on the OpenGL surface
```

There is **no Java-side glucose data buffer**. The C++ renderer reads directly from the
native sensor structs on every frame. Java never sees individual glucose points; it only
receives the summary callback `doglucose(name,mgdl,glu,rate,...)` for notifications/widgets.

**Migration consequence:**  
`NativeGraphDataRepository` (Phase 2) must pull glucose data out of C++ via a new JNI method
that returns a snapshot array — e.g.:
```java
public static native float[] getGlucoseSnapshot(long startMs, long endMs);
// returns flat array: [t0, v0, sensorIdx0,  t1, v1, sensorIdx1, ...]
```
This new method must be added to `javacurve.cpp` **during Phase 2** as part of the data bridge.
It does **not** change any rendering logic (is additive only, consistent with the freeze).

---

## 5. Settings Values Required by the Vico Chart

These must be exposed via `ChartSettings` in the `GraphDataRepository` interface:

| Setting | Source | Type |
|---------|--------|------|
| `targetLow` | `Natives.targetlow()` | `Float` (mg/dL) |
| `targetHigh` | `Natives.targethigh()` | `Float` (mg/dL) |
| `graphLow` | `Natives.graphlow()` | `Float` (Y-axis min) |
| `graphHigh` | `Natives.graphhigh()` | `Float` (Y-axis max) |
| `unit` | `Natives.getunit()` | `Int` (0=mg/dL, 1=mmol/L) |
| `invertColors` | `Natives.getInvertColors()` | `Boolean` |
| `showScans` | `Natives.getshowscans()` | `Boolean` |
| `showHistories` | `Natives.getshowhistories()` | `Boolean` |
| `showStream` | `Natives.getshowstream()` | `Boolean` |
| `showNumbers` | `Natives.getshownumbers()` | `Boolean` |
| `screenOrientation` | `Natives.getScreenOrientation()` | `Int` (ActivityInfo constant) |

---

## 6. Parity Gaps

_To be filled in during Phase 3 visual parity testing._

| Feature | Current Renderer | Vico Equivalent | Gap / Notes |
|---------|-----------------|-----------------|-------------|
| Per-sensor colour coding | NanoVG `NVGcolor *colors[]` | Vico `LineSpec` per series | Need to map sensor index → Material colour |
| Target-range fill band | Custom NanoVG rect | Vico `ThresholdLine` | ThresholdLine is lines only; custom `CartesianLayer` needed for filled band |
| Percentile shading (10–90%) | `percentile.cpp` NanoVG fills | Custom `CartesianLayer` | Full re-implementation required |
| Statistics overlay (`showstats`) | NanoVG text+bars drawn in-GL | Compose Column/Row outside chart | Low risk — pure Compose |
| Long-press data-point info | `Natives.longpress(x,y)` → hitptr | Vico `Marker` + `MarkerVisibilityListener` | Must map Vico marker index → `hitptr` |
| Scan vs. stream vs. history layers | 3 separate NanoVG draw passes | 3 separate Vico `LineLayer`s | Manageable |
| Bad-scan error overlay | `Natives.badscan(kind)` fullscreen message | Compose `Box` overlay | Simple |
| Number list (side panel) | Custom `numcontrol` ViewGroup overlay | Existing `Layout`-based overlay retained | No change in Phase 3 |
| RTL mirror | NanoVG coord flip + font | Vico + `LayoutDirection.Rtl` | Needs testing |

---

## 7. `jugglucotext` String Fields — Migration to `strings.xml`

> **Phase 3 task.** Every field in the table below is a chart-canvas-only string currently
> stored in a C++ struct. None exist in any `strings.xml`. All must be added as Android string
> resources before the Vico chart can display them. The content is already translated in the
> corresponding `*jugglucotext.cpp` files for all 17 locales.
>
> **Arabic/Hebrew note:** Strings stored with the compile-time `RTL()` reversal macro must be
> saved in their natural logical Unicode order in `strings.xml` — Android's BiDi engine handles
> visual reordering automatically. The macro is an artefact of NanoVG's inability to do BiDi
> shaping.

### 7.1 Calendar Labels (arrays — 19 entries × 17 locales)

| C++ field | Type | Content |
|-----------|------|---------|
| `daylabel[7]` | `char[7][16]` | Abbreviated day names: Sun–Sat |
| `speakdaylabel[7]` | `char[7][25]` | TTS-spoken day names (longer form) |
| `monthlabel[12]` | `char[12][16]` | Abbreviated month names: Jan–Dec |

### 7.2 Sensor Status Strings (~11 entries)

| C++ field | Example (English) | Notes |
|-----------|-------------------|-------|
| `scanned` | `"Scanned"` | Shown after an NFC scan |
| `readysecEnable` | `"Sensor ready in %d min. Scan again to enable streaming."` | printf format |
| `readysec` | `"Sensor ready in %d min."` | printf format |
| `sensorstarted` | `"Sensor started"` | |
| `lastscanned` | `"Last scanned"` | |
| `laststream` | `"Last stream"` | |
| `sensorends` | `"Sensor ends"` | |
| `sensorexpectedend` | `"Expected end"` | |
| `endedformat` | `"Ended %s"` | printf format |
| `notreadyformat` | `"Not ready: %s"` | printf format |
| `history` / `historyinfo` / `history3info` | `"History"` / info strings | |

### 7.3 Statistics & Analysis Labels (~10 entries, mobile only)

| C++ field | Example (English) | Notes |
|-----------|-------------------|-------|
| `median` | `"Median"` | Percentile chart label |
| `middle` | `"Middle 50%"` | |
| `averageglucose` | `"Average glucose"` | RTL: label+value order inverted |
| `duration` | `"Duration: %d days"` | printf format |
| `timeactive` | `"Time active: %.0f%%"` | printf format |
| `nrmeasurement` | `"Measurements: %d"` | printf format |
| `EstimatedA1C` | `"eA1c: %.1f%% / %.1f mmol/mol"` | printf format |
| `GMI` | `"GMI: %.1f%% / %.1f mmol/mol"` | printf format |
| `SD` | `"SD: %.1f"` | printf format |
| `glucose_variability` | `"CV: %.1f%%"` | printf format |
| `newamount` | `"New amount"` | |

### 7.4 Error / Connection Messages (~8 entries)

| C++ field | Example (English) |
|-----------|-------------------|
| `networkproblem` | `"No glucose from mirror"` |
| `enablebluetooth` | `"Enable Bluetooth"` |
| `useBluetoothOff` | `"Bluetooth off"` |
| `noconnectionerror` | `"No connection"` |
| `stsensorerror` | `"Sensor error"` |
| `streplacesensor` | `"Replace sensor"` |
| `nolocationpermission` | `"No location permission"` |
| `nonearbydevicespermission` | `"No nearby devices permission"` |
| `needsandroid8` | `"Requires Android 8"` |
| `unsupportedSibionics` | `"Unsupported Sibionics"` |
| `waitingforconnection` | `"Waiting for connection"` |
| `receivingpastvalues` | `"Receiving past values"` |
| `receivingdata` | `"Receiving data"` |

### 7.5 Trend / Direction Names (6 entries)

| C++ field | Values |
|-----------|--------|
| `Undetermined` | `"Undetermined"` |
| `FallingQuickly` | `"Falling quickly"` |
| `Falling` | `"Falling"` |
| `Stable` | `"Stable"` |
| `Rising` | `"Rising"` |
| `RisingQuickly` | `"Rising quickly"` |

_(These are also used for TTS — `getTrendName(int type)` returns the `string_view`. In the
Kotlin path these will come from `resources.getString(R.string.trend_stable)` etc.)_

### 7.6 Menu Labels (mobile only — 4 menus × up to 8 items)

| C++ field | Description |
|-----------|-------------|
| `menustr0[8]` | Main tap menu (8 items) |
| `menustr1[7]` | Secondary menu (7 items) |
| `menustr2[7]` | Tertiary menu (7 items) |
| `menustr3[7]` | Quaternary menu (7 items) |
| `checked` / `unchecked` | TTS toggle state labels |

_(Menu labels are drawn in the OpenGL overlay, not in XML layouts. They must become
`strings.xml` entries or be moved to the existing Compose menu UI in Phase 3.)_

### 7.7 Miscellaneous

| C++ field | Example (English) |
|-----------|-------------------|
| `summarygraph` | `"Summary"` |
| `logdays` | `"Log days"` |
| `unhide` | `"Unhide"` |
| `deleted` | `"Deleted"` |
| `advancedstart` | Advanced sensor info prefix |

### 7.8 RTL-Specific Migration Notes

| Concern | Current C++ approach | Kotlin/Compose replacement |
|---------|---------------------|---------------------------|
| Arabic string storage | Compile-time `RTL()` macro reverses byte order | Store in normal logical Unicode order in `strings.xml` |
| Arabic visual shaping | Manual `rtl_to_logical_utf8()` before every draw/speak call | Android BiDi engine handles automatically |
| Hebrew support | `#ifdef USE_HEBREW` compile flag; separate `iwjugglucotext.cpp` | Standard locale in `strings.xml`; `LayoutDirection.Rtl` from Android config |
| Chart axis direction | Hardcoded LTR via `NVG_ALIGN_LEFT` constants | `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` wrapping `CartesianChartHost` |
| Stat panel direction | Forced LTR by accident in some places | Explicit `LayoutDirection.Rtl` when `ChartSettings.isRtl == true` |
| TTS Arabic reordering | `rtl_to_logical_utf8()` called before `speak()` | Pass raw Unicode string; Android TTS handles Arabic natively |
