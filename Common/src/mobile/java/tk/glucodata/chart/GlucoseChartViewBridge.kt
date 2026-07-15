/*
 * Phase 3 — Vico Integration
 *
 * Java-callable bridge that sets up the ComposeView for MainActivity.
 * This file exists because @Composable functions cannot be called directly from Java;
 * all Compose interop must go through this Kotlin file.
 */
package tk.glucodata.chart

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider

/**
 * Creates and returns a [ComposeView] hosting [GlucoseChartScreen].
 * Designed to be called from Java (MainActivity.startdisplay).
 *
 * @param activity    The host Activity — used to scope the ViewModel.
 * @param onTap       Called when the user single-taps anywhere on the chart background.
 *                    Use to open the app menu (replaces the C++ tap-routing in Vico mode).
 * @param onLongPress Called back (on the main thread) when the user long-presses a data point.
 *                    Receives the closest [GlucoseEntry].
 */
@JvmOverloads
fun createGlucoseChartView(
    activity: ComponentActivity,
    onTap: (() -> Unit) = {},
    onLongPress: ((GlucoseEntry) -> Unit) = {},
): ComposeView {
    val viewModel = ViewModelProvider(
        activity,
        GlucoseChartViewModel.Factory(NativeGraphDataRepository.instance),
    )[GlucoseChartViewModel::class.java]

    return ComposeView(activity).apply {
        setContent {
            GlucoseChartScreen(
                viewModel   = viewModel,
                onTap       = onTap,
                onLongPress = onLongPress,
            )
        }
    }
}
