![value](valuemmolL.png)

## Chart Rendering — Mobile Build

The `mobile` product flavor ships **both** a modern **Jetpack Compose + [Vico 3.x](https://patrykandpatrick.com/vico/)** chart
and the original C++/OpenGL ES 2 / NanoVG renderer. Users can switch between them at runtime.

### What changed

| Area | Legacy UI (OpenGL/NanoVG) | New UI (Vico/Compose) |
|---|---|---|
| Rendering | C++ NanoVG (OpenGL ES 2) | Kotlin Jetpack Compose + Vico 3.x |
| Theming | Manual C++ colour tables | Material 3 / Material You (`dynamicColorScheme`) |
| Orientation | Hard-locked landscape | Free rotation (portrait + landscape, user-configurable) |
| Dark mode | C++ invert flag | `isSystemInDarkTheme() xor invertColors` |
| Gestures | C++ touch callbacks | Vico built-in scroll, fling, pinch-zoom |
| Stats panel | C++ NanoVG overlays | Compose: latest value, trend arrow (↑↑/↑/→/↓/↓↓), TIR 24h |
| Sensor colours | Single colour | Per-sensor palette (blue/green/red/amber) |
| Target bands | C++ drawn lines | Vico `HorizontalLine` decorations |
| Accessibility | None | `contentDescription` / `semantics` on all chart elements |

### Switching between UIs

Open **Settings → "Use new chart (Vico)"** checkbox:

- **Checked** (default): Vico/Compose chart, free rotation (portrait + landscape).
- **Unchecked**: Legacy OpenGL/NanoVG chart, always landscape (original behaviour).

The preference is persisted across app restarts. The activity recreates immediately on toggle.

### WearOS

The WearOS flavor is **unchanged** — it continues to use the C++/OpenGL/NanoVG renderer.

### Architecture

Both UIs share all non-rendering app logic (NFC, Bluetooth, dialogs, settings, alarms).
`GlucoseCurve` is always created and acts as a headless helper for menus, NFC callbacks,
and dialogs even when the Vico chart is the active content view.

The runtime switcher is `Applic.useVicoChart()` / `Applic.setUseVicoChart(boolean)`,
backed by `SharedPreferences` (key `use_vico_chart`, default `true`).
