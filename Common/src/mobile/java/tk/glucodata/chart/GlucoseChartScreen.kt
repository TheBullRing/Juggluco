/*
 * Phase 3 / Phase 6 — Vico Integration + Polish
 *
 * Main Compose entry-point for the glucose chart.
 * Hosted inside a ComposeView that replaces GlucoseCurve when USE_VICO_CHART = true.
 *
 * Layout is responsive:
 *   - Portrait / compact width  (< 600 dp): chart stacked above stats panel.
 *   - Landscape / expanded width (≥ 600 dp): chart left, stats panel right.
 *
 * RTL rules:
 *   - CartesianChartHost is wrapped in LayoutDirection.Ltr so the time axis
 *     always runs left → right regardless of device locale.
 *   - Surrounding stat/sensor info panels use the locale direction
 *     (RTL when ChartSettings.isRtl == true).
 *
 * Threading: all ViewModel StateFlows are collected on the main thread via
 * collectAsStateWithLifecycle.
 *
 * Vico 3.x API notes (verified against compose-android 3.2.3 AAR):
 *   - Only artifact: com.patrykandpatrick.vico:compose-m3:3.2.3
 *   - Axes: HorizontalAxis.rememberBottom() / VerticalAxis.rememberStart()
 *   - HorizontalLine is in compose.cartesian.decoration
 *   - CartesianLayerRangeProvider.auto() for data-range Y-axis scaling
 *   - x-values must be Unix SECONDS (not ms) — Float precision would lose
 *     ~5 digits at the ms epoch magnitude (~1.75e12)
 *   - valueFormatter must never return blank — Vico 3.x throws IllegalStateException
 */
package tk.glucodata.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

// ─────────────────────────────────────────────────────────────────────────────
// Sensor colour palette (cycles if more than 4 sensors active)
// ─────────────────────────────────────────────────────────────────────────────

private val SENSOR_COLORS = listOf(
    Color(0xFF1E88E5),  // Blue 600     — sensor 0
    Color(0xFF43A047),  // Green 600    — sensor 1
    Color(0xFFE53935),  // Red 600      — sensor 2
    Color(0xFFFF8F00),  // Amber 800    — sensor 3
)

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable. Call from the ComposeView created in MainActivity.startdisplay()
 * when [tk.glucodata.Applic.USE_VICO_CHART] is true.
 *
 * @param viewModel   The chart ViewModel (created via [GlucoseChartViewModel.Factory]).
 * @param onTap       Called on single-tap — opens the app menu.
 * @param onLongPress Invoked when the user long-presses a data point.
 *                    Receives the closest [GlucoseEntry] so the existing number-entry
 *                    dialog can be opened. Replaces Natives.longpress(x, y).
 */
@Composable
fun GlucoseChartScreen(
    viewModel: GlucoseChartViewModel,
    onTap: () -> Unit = {},
    onLongPress: (GlucoseEntry) -> Unit = {},
) {
    val entries  by viewModel.glucoseEntries.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    GlucoseTheme(invertColors = settings.invertColors) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Glucose chart screen" },
            color = MaterialTheme.colorScheme.background,
        ) {
            // systemBarsPadding() keeps content clear of both the status bar (top)
            // and the navigation bar (bottom / sides in landscape).
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > 600.dp
                    if (isLandscape) {
                        LandscapeLayout(entries, viewport, settings, onLongPress)
                    } else {
                        PortraitLayout(entries, viewport, settings, onLongPress)
                    }
                }
                // Menu FAB — floats top-end corner, above Vico's gesture area.
                FloatingActionButton(
                    onClick        = onTap,
                    modifier       = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .semantics { contentDescription = "Open menu" },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        painter            = painterResource(android.R.drawable.ic_menu_more),
                        contentDescription = null, // described by FAB semantics above
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout variants
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeLayout(
    entries:     List<GlucoseEntry>,
    viewport:    ChartViewport,
    settings:    ChartSettings,
    onLongPress: (GlucoseEntry) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        GlucoseChart(
            entries     = entries,
            settings    = settings,
            onLongPress = onLongPress,
            modifier    = Modifier.weight(1f).fillMaxHeight(),
        )
        Spacer(modifier = Modifier.width(4.dp))
        StatsPanelWithDirection(
            entries  = entries,
            settings = settings,
            modifier = Modifier.width(200.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun PortraitLayout(
    entries:     List<GlucoseEntry>,
    viewport:    ChartViewport,
    settings:    ChartSettings,
    onLongPress: (GlucoseEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GlucoseChart(
            entries     = entries,
            settings    = settings,
            onLongPress = onLongPress,
            modifier    = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        StatsPanelWithDirection(
            entries  = entries,
            settings = settings,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }
}

@Composable
private fun StatsPanelWithDirection(
    entries:  List<GlucoseEntry>,
    settings: ChartSettings,
    modifier: Modifier = Modifier,
) {
    val direction = if (settings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        StatsPanel(entries, settings, modifier)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlucoseChart(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    onLongPress: (GlucoseEntry) -> Unit,
    modifier:    Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Group entries by sensor index; sorted so series order is deterministic.
    val seriesMap = remember(entries) {
        entries.groupBy { it.sensorIndex }.toSortedMap()
    }

    // Use Unix time in SECONDS (not ms) as x-values.
    // Unix ms timestamps are ~1.75e12 — a Float only has 7 significant digits,
    // so storing them as Float loses ~5 digits of precision and makes Vico's
    // auto-tick positions land on values that don't correspond to real times.
    // Dividing by 1000 first (→ ~1.75e9 seconds) keeps the precision adequate
    // for 5-minute glucose reading intervals.
    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            if (seriesMap.isEmpty()) {
                // Empty placeholder keeps the chart composable valid before data loads.
                lineModel { series(listOf(0f)) }
            } else {
                // All sensor series go into ONE lineModel call as separate series().
                // Vico's LineProvider.series(vararg Line) maps series index → Line,
                // so sensor 0 → SENSOR_COLORS[0], sensor 1 → SENSOR_COLORS[1], etc.
                lineModel {
                    seriesMap.values.forEach { sensorEntries ->
                        val xValues = sensorEntries.map { (it.timestampMs / 1000L).toFloat() }
                        val yValues = sensorEntries.map {
                            if (settings.unit == GlucoseUnit.MMOL) it.valueMgdL / 18.0f
                            else it.valueMgdL
                        }
                        series(x = xValues, y = yValues)
                    }
                }
            }
        }
    }

    // ── Target-range horizontal threshold lines ───────────────────────────────
    // targetLow/targetHigh are already in display units (returned by gconvert()
    // in the C++ layer). Do NOT call yValue() on them — that would double-convert.
    val targetLowY  = settings.targetLow
    val targetHighY = settings.targetHigh

    val lowFill  = Fill(Color(0xFFE53935))  // Material Red 600
    val highFill = Fill(Color(0xFFFB8C00))  // Material Orange 600

    val lowLine  = rememberLineComponent(fill = lowFill,  thickness = 1.5.dp)
    val highLine = rememberLineComponent(fill = highFill, thickness = 1.5.dp)

    val targetLowLine  = remember(targetLowY,  lowLine)  {
        HorizontalLine(y = { targetLowY.toDouble()  }, line = lowLine)
    }
    val targetHighLine = remember(targetHighY, highLine) {
        HorizontalLine(y = { targetHighY.toDouble() }, line = highLine)
    }

    // ── Per-sensor line colours ───────────────────────────────────────────────
    // rememberLine is a top-level @Composable extension on LineCartesianLayer.Companion
    // (package com.patrykandpatrick.vico.compose.cartesian.layer), imported explicitly.
    // Using the non-deprecated overload that takes `interpolator` instead of `pointConnector`.
    //
    // LineProvider.series(vararg Line) routes series by index:
    //   series 0 (sensor 0) → line0 (Blue)
    //   series 1 (sensor 1) → line1 (Green)
    //   series 2 (sensor 2) → line2 (Red)
    //   series 3 (sensor 3) → line3 (Amber)
    val line0 = LineCartesianLayer.rememberLine(
        fill         = LineCartesianLayer.LineFill.single(Fill(SENSOR_COLORS[0])),
        stroke       = LineCartesianLayer.LineStroke.Continuous(),
        interpolator = LineCartesianLayer.Interpolator.Sharp,
    )
    val line1 = LineCartesianLayer.rememberLine(
        fill         = LineCartesianLayer.LineFill.single(Fill(SENSOR_COLORS[1])),
        stroke       = LineCartesianLayer.LineStroke.Continuous(),
        interpolator = LineCartesianLayer.Interpolator.Sharp,
    )
    val line2 = LineCartesianLayer.rememberLine(
        fill         = LineCartesianLayer.LineFill.single(Fill(SENSOR_COLORS[2])),
        stroke       = LineCartesianLayer.LineStroke.Continuous(),
        interpolator = LineCartesianLayer.Interpolator.Sharp,
    )
    val line3 = LineCartesianLayer.rememberLine(
        fill         = LineCartesianLayer.LineFill.single(Fill(SENSOR_COLORS[3])),
        stroke       = LineCartesianLayer.LineStroke.Continuous(),
        interpolator = LineCartesianLayer.Interpolator.Sharp,
    )

    // ── Y-axis range: data-driven with padding, never starting from 0 ─────────
    // minY = min(dataMin, targetLow) - 10% of display range
    // maxY = max(dataMax, targetHigh) + 5% of display range
    // This keeps threshold lines visible and removes wasted space below data.
    val allDisplayValues = remember(entries, settings) {
        entries.map { toDisplayUnit(it.valueMgdL, settings.unit) }
    }
    val dataMinY = if (allDisplayValues.isEmpty()) targetLowY  else allDisplayValues.min()
    val dataMaxY = if (allDisplayValues.isEmpty()) targetHighY else allDisplayValues.max()
    val rangeMinY = minOf(dataMinY, targetLowY)
    val rangeMaxY = maxOf(dataMaxY, targetHighY)
    val rangePad  = (rangeMaxY - rangeMinY) * 0.08f
    val yMin = (rangeMinY - rangePad).toDouble()
    val yMax = (rangeMaxY + rangePad).toDouble()

    val rangeProvider = remember(yMin, yMax) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = yMin
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = yMax
        }
    }

    // ── Line layer ────────────────────────────────────────────────────────────
    val lineLayer = rememberLineCartesianLayer(
        lineProvider  = LineCartesianLayer.LineProvider.series(line0, line1, line2, line3),
        rangeProvider = rangeProvider,
    )
    val lineLayers = listOf(lineLayer)

    // ── Marker (long-press data-point info) ───────────────────────────────────
    val markerLabelColor = MaterialTheme.colorScheme.onSurface
    val marker = rememberDefaultCartesianMarker(
        label     = rememberTextComponent(style = TextStyle(color = markerLabelColor)),
        indicator = { color -> ShapeComponent(fill = Fill(color), shape = CircleShape) },
    )

    // ── Scroll / zoom ─────────────────────────────────────────────────────────
    // autoScrollCondition = always: ensures scroll-to-end fires every time the
    // model updates, including the very first load (data arrives asynchronously).
    val scrollState = rememberVicoScrollState(
        initialScroll        = Scroll.Absolute.End,
        autoScroll           = Scroll.Absolute.End,
        autoScrollCondition  = AutoScrollCondition { _, _ -> true },
    )
    val zoomState = rememberVicoZoomState()

    // ── Chart host ────────────────────────────────────────────────────────────
    // Force LTR so the time axis always runs left → right regardless of locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                *lineLayers.toTypedArray(),
                startAxis  = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        style = TextStyle(
                            color    = MaterialTheme.colorScheme.onBackground,
                            fontSize = 11.sp,
                        )
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        style = TextStyle(
                            color    = MaterialTheme.colorScheme.onBackground,
                            fontSize = 11.sp,
                        )
                    ),
                    // Show a time label every ~30 minutes of data.
                    // x-values are Unix seconds; 5-min readings → 1 tick per 6 readings = 30 min.
                    // aligned() takes (ExtraStore) -> Int lambdas, not plain Int values.
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { _ -> 6 }),
                    valueFormatter = { _, value, _ ->
                        // x-values are Unix seconds (~1.75e9).
                        // Vico 3.x throws on blank strings — always return a non-empty label.
                        val timeSec = value.toLong().coerceAtLeast(0L)
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = timeSec * 1000L
                        }
                        "%02d:%02d".format(
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE)
                        )
                    }
                ),
                decorations = listOf(targetLowLine, targetHighLine),
                marker      = marker,
            ),
            modelProducer = modelProducer,
            scrollState   = scrollState,
            zoomState     = zoomState,
            modifier      = modifier
                .padding(8.dp)
                .semantics { contentDescription = "Glucose time series chart" },
        )
    }
}

/**
 * Converts a raw mg/dL value to the display unit.
 * GlucoseEntry.valueMgdL is always stored as mg/dL by streamfromSensorptr.
 * targetLow/targetHigh are already in display units (returned by gconvert()).
 * This function is used to bring sensor values into the same unit as the thresholds.
 */
private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

// ─────────────────────────────────────────────────────────────────────────────
// Stats panel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stats panel showing the latest reading, trend arrow, and time-in-range.
 * Placed below the chart in portrait; to the right in landscape.
 *
 * All comparisons against targetLow/targetHigh use display-unit values
 * (toDisplayUnit applied to valueMgdL) to match the units returned by
 * gconvert() in the C++ layer.
 */
@Composable
private fun StatsPanel(
    entries:  List<GlucoseEntry>,
    settings: ChartSettings,
    modifier: Modifier = Modifier,
) {
    val latest   = entries.maxByOrNull { it.timestampMs }
    val previous = entries
        .filter { latest == null || it.timestampMs < latest.timestampMs }
        .maxByOrNull { it.timestampMs }

    // Convert latest reading to display unit for comparisons and display
    val latestDisplay  = latest?.let  { toDisplayUnit(it.valueMgdL, settings.unit) }
    val previousDisplay = previous?.let { toDisplayUnit(it.valueMgdL, settings.unit) }

    // Current reading display string
    val displayValue = latestDisplay?.let {
        if (settings.unit == GlucoseUnit.MMOL)
            "%.1f mmol/L".format(it)
        else
            "${it.toInt()} mg/dL"
    } ?: "—"

    // Trend arrow: delta in display units, thresholds also in display units
    val trendArrow = when {
        latestDisplay == null || previousDisplay == null -> ""
        else -> {
            // Use mg/dL-scale deltas regardless of display unit for consistent thresholds
            val deltaMgdl = latest.valueMgdL - previous.valueMgdL
            when {
                deltaMgdl > 10f  -> "↑↑"
                deltaMgdl > 4f   -> "↑"
                deltaMgdl < -10f -> "↓↓"
                deltaMgdl < -4f  -> "↓"
                else             -> "→"
            }
        }
    }

    // Time-in-range over the last 24 hours — compare display units to display-unit thresholds
    val now = System.currentTimeMillis()
    val cutoff = now - 24L * 3600L * 1000L
    val recent = entries.filter { it.timestampMs >= cutoff }
    val tirText = if (recent.isEmpty()) {
        ""
    } else {
        val low  = settings.targetLow
        val high = settings.targetHigh
        val inRange = recent.count { toDisplayUnit(it.valueMgdL, settings.unit) in low..high }
        val pct = (inRange * 100) / recent.size
        "TIR 24h: $pct%"
    }

    // Glucose colour: compare display-unit value against display-unit thresholds
    val glucoseColor = when {
        latestDisplay == null                    -> MaterialTheme.colorScheme.onSurfaceVariant
        latestDisplay < settings.targetLow       -> Color(0xFFE53935)
        latestDisplay > settings.targetHigh      -> Color(0xFFFF8F00)
        else                                     -> Color(0xFF43A047)
    }

    Box(
        modifier         = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Latest glucose: $displayValue $trendArrow" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = glucoseColor,
                    fontWeight = FontWeight.Bold,
                )
                if (trendArrow.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = trendArrow,
                        style = MaterialTheme.typography.headlineSmall,
                        color = glucoseColor,
                    )
                }
            }
            if (tirText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = tirText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies Material 3 / Material You theme.
 * Uses dynamic colour on Android 12+ (API 31) and a static fallback on older devices.
 * [invertColors] is XOR-ed with the system dark-mode preference so the app's own
 * inverted-display toggle works correctly.
 */
@Composable
private fun GlucoseTheme(
    invertColors: Boolean,
    content: @Composable () -> Unit,
) {
    val context   = LocalContext.current
    val darkTheme = isSystemInDarkTheme() xor invertColors

    val colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
