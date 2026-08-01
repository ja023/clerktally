package com.vague.crewtally.ui.theme

import androidx.compose.ui.unit.dp

/**
 * All fixed dp values live here. No screen or component may use a raw dp literal; every
 * spacing gap, touch target, and line weight comes from this object (read via
 * `CrewTallyTheme.dimens`).
 *
 * Touch targets are sized for the ~55-year-old primary user: nothing tappable is below
 * [minTarget] (48dp), and the daily primary actions (attendance toggles, the pay button)
 * are [primaryTarget] (56dp) so they are hard to miss.
 */
object CrewTallyDimens {
    // --- Spacing scale ---------------------------------------------------------------
    val spaceNone = 0.dp
    val spaceXxs = 2.dp
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    val spaceXxl = 28.dp
    val spaceXxxl = 40.dp

    /** Default horizontal padding at screen edges (generous for readability). */
    val screenEdge = 20.dp

    /** Vertical gap between major sections on a screen. */
    val sectionGap = 28.dp

    /** Comfortable vertical padding inside a list row. */
    val rowVertical = 16.dp

    // --- Touch targets (accessibility floor) -----------------------------------------
    /** Smallest allowed tappable target. Nothing interactive may be smaller. */
    val minTarget = 48.dp

    /** Primary daily actions (attendance toggle, pay button, primary CTA). */
    val primaryTarget = 56.dp

    /** Bottom navigation bar item minimum footprint. */
    val navItemMin = 56.dp

    /** Minimum height of a standard list row (title + optional subtitle). */
    val listRowMinHeight = 64.dp

    // --- Line weights ----------------------------------------------------------------
    val borderThin = 1.dp
    val strokeThin = 2.dp

    // --- Icon sizes ------------------------------------------------------------------
    val iconSm = 20.dp
    val iconMd = 24.dp
    val iconLg = 28.dp

    // --- Elevation -------------------------------------------------------------------
    val elevationNone = 0.dp
    val elevationCard = 1.dp
    val elevationRaised = 3.dp
}
