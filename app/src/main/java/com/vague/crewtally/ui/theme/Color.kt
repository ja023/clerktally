package com.vague.crewtally.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Raw CrewTally palette. This is the ONLY place literal hex color values may appear
 * in the whole app. Screens and components read semantic roles through
 * `MaterialTheme.colorScheme` (provided by [CrewTallyTheme]) — never a Color literal.
 *
 * The palette is deliberately high-contrast: the primary user is ~55 and the app is
 * used outdoors on job sites, so every foreground/background pair here clears WCAG 2.2
 * AA contrast for body text.
 */
internal object CrewTallyPalette {
    // Brand — a deep, calm work-blue. Trustworthy, high contrast on both surfaces.
    val Blue = Color(0xFF1F4E79)
    val BlueLight = Color(0xFF3A6FA0)
    val BlueDark = Color(0xFF16385A)
    val BlueContainer = Color(0xFFD3E4F5)
    val OnBlueContainer = Color(0xFF0B2740)

    // Dark-theme-only outlined-button token (v1.2 a11y fix). BlueLight (used as dark `primary`)
    // only reaches ~3.5:1 against SurfaceDark (#121316) — below the 4.5:1 floor CrewTallyOutlinedButton
    // needs once v1.2 promotes it to primary weight (full-width on the clerk profile + balance
    // screen). This lighter blue clears ~5.76:1 there (WCAG relative-luminance formula) — see
    // [CrewTallyDarkExtraColors] for where it's used.
    val BlueOutlineDark = Color(0xFF5A94C9)

    // Accent for money-positive / confirmations.
    val Green = Color(0xFF2E7D46)
    val GreenContainer = Color(0xFFC9EBD2)
    val OnGreenContainer = Color(0xFF0E3A1D)

    // Neutrals — light theme.
    val Ink = Color(0xFF1A1C1E)
    val InkMuted = Color(0xFF44474B)
    val SurfaceLight = Color(0xFFFBFBFD)
    val SurfaceLightRaised = Color(0xFFFFFFFF)
    val SurfaceLightVariant = Color(0xFFE2E6EC)
    val OutlineLight = Color(0xFF73777C)
    val OutlineVariantLight = Color(0xFFC3C7CD)

    // Neutrals — dark theme.
    val Paper = Color(0xFFE3E2E6)
    val PaperMuted = Color(0xFFC3C7CD)
    val SurfaceDark = Color(0xFF121316)
    val SurfaceDarkRaised = Color(0xFF1E2023)
    val SurfaceDarkVariant = Color(0xFF42474E)
    val OutlineDark = Color(0xFF8D9199)
    val OutlineVariantDark = Color(0xFF42474E)

    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Money-negative / destructive.
    val Danger = Color(0xFFB3261E)
    val DangerContainer = Color(0xFFF9DEDC)
    val OnDangerContainer = Color(0xFF410E0B)
    val DangerDark = Color(0xFFF2B8B5)
    val DangerContainerDark = Color(0xFF8C1D18)
    val OnDangerContainerDark = Color(0xFFF9DEDC)
}

/**
 * Light Material 3 scheme. Semantic roles map onto [CrewTallyPalette]. Anything a
 * screen needs (primary action color, surfaces, text colors, error) is reachable via
 * `MaterialTheme.colorScheme.*`.
 */
val CrewTallyLightScheme = lightColorScheme(
    primary = CrewTallyPalette.Blue,
    onPrimary = CrewTallyPalette.White,
    primaryContainer = CrewTallyPalette.BlueContainer,
    onPrimaryContainer = CrewTallyPalette.OnBlueContainer,
    secondary = CrewTallyPalette.Green,
    onSecondary = CrewTallyPalette.White,
    secondaryContainer = CrewTallyPalette.GreenContainer,
    onSecondaryContainer = CrewTallyPalette.OnGreenContainer,
    tertiary = CrewTallyPalette.BlueLight,
    onTertiary = CrewTallyPalette.White,
    background = CrewTallyPalette.SurfaceLight,
    onBackground = CrewTallyPalette.Ink,
    surface = CrewTallyPalette.SurfaceLight,
    onSurface = CrewTallyPalette.Ink,
    surfaceVariant = CrewTallyPalette.SurfaceLightVariant,
    onSurfaceVariant = CrewTallyPalette.InkMuted,
    surfaceContainer = CrewTallyPalette.SurfaceLightRaised,
    surfaceContainerHigh = CrewTallyPalette.SurfaceLightRaised,
    outline = CrewTallyPalette.OutlineLight,
    outlineVariant = CrewTallyPalette.OutlineVariantLight,
    error = CrewTallyPalette.Danger,
    onError = CrewTallyPalette.White,
    errorContainer = CrewTallyPalette.DangerContainer,
    onErrorContainer = CrewTallyPalette.OnDangerContainer,
    scrim = CrewTallyPalette.Black,
)

/** Dark Material 3 scheme — same semantic roles, inverted neutrals, brightened brand. */
val CrewTallyDarkScheme = darkColorScheme(
    primary = CrewTallyPalette.BlueLight,
    // White, not OnBlueContainer: BlueLight is a mid-tone brand blue, and OnBlueContainer
    // (#0B2740 on #3A6FA0 ~= 2.87:1) fails WCAG AA. White clears ~5.3:1, matching the
    // tertiary/onTertiary pairing below which uses the same BlueLight surface.
    onPrimary = CrewTallyPalette.White,
    primaryContainer = CrewTallyPalette.BlueDark,
    onPrimaryContainer = CrewTallyPalette.BlueContainer,
    secondary = CrewTallyPalette.Green,
    onSecondary = CrewTallyPalette.White,
    secondaryContainer = CrewTallyPalette.OnGreenContainer,
    onSecondaryContainer = CrewTallyPalette.GreenContainer,
    tertiary = CrewTallyPalette.BlueLight,
    onTertiary = CrewTallyPalette.White,
    background = CrewTallyPalette.SurfaceDark,
    onBackground = CrewTallyPalette.Paper,
    surface = CrewTallyPalette.SurfaceDark,
    onSurface = CrewTallyPalette.Paper,
    surfaceVariant = CrewTallyPalette.SurfaceDarkVariant,
    onSurfaceVariant = CrewTallyPalette.PaperMuted,
    surfaceContainer = CrewTallyPalette.SurfaceDarkRaised,
    surfaceContainerHigh = CrewTallyPalette.SurfaceDarkRaised,
    outline = CrewTallyPalette.OutlineDark,
    outlineVariant = CrewTallyPalette.OutlineVariantDark,
    error = CrewTallyPalette.DangerDark,
    onError = CrewTallyPalette.OnDangerContainer,
    errorContainer = CrewTallyPalette.DangerContainerDark,
    onErrorContainer = CrewTallyPalette.OnDangerContainerDark,
    scrim = CrewTallyPalette.Black,
)

/**
 * Design tokens that don't map onto a Material 3 [androidx.compose.material3.ColorScheme] role
 * but still need to differ between light and dark theme. New fields belong here, not squeezed
 * into an existing ColorScheme role that wasn't designed for them — see [CrewTallyDarkExtraColors]
 * for why `colorScheme.primary` itself couldn't be reused for this.
 */
data class CrewTallyExtraColors(
    val outlinedButtonContent: Color,
    val outlinedButtonBorder: Color,
)

/**
 * Light theme: [CrewTallyOutlinedButton][com.vague.crewtally.ui.components.CrewTallyOutlinedButton]
 * keeps its original colors here, mirroring [CrewTallyLightScheme]'s `primary` / `outline` as
 * literals — light theme was never the contrast problem (v1.2 a11y fix), so it stays exactly
 * as it was.
 */
val CrewTallyLightExtraColors = CrewTallyExtraColors(
    outlinedButtonContent = CrewTallyPalette.Blue,
    outlinedButtonBorder = CrewTallyPalette.OutlineLight,
)

/**
 * Dark theme needs its own token: `colorScheme.primary` there is [CrewTallyPalette.BlueLight],
 * which only reaches ~3.5:1 against the dark background (#121316) — below the 4.5:1 floor for
 * 18sp SemiBold label text once v1.2 promotes this button to primary weight (two full-width
 * buttons on the clerk profile, one on the balance screen). [CrewTallyPalette.BlueOutlineDark]
 * clears ~5.76:1 (verified with the WCAG relative-luminance formula) and is used for BOTH the
 * label and the border, so the outline reads as a matching color rather than a mismatched grey
 * ring. `colorScheme.primary` itself is deliberately left untouched here — filled buttons and
 * their onPrimary contrast (fixed separately in Phase 6) are unaffected by this change.
 */
val CrewTallyDarkExtraColors = CrewTallyExtraColors(
    outlinedButtonContent = CrewTallyPalette.BlueOutlineDark,
    outlinedButtonBorder = CrewTallyPalette.BlueOutlineDark,
)
