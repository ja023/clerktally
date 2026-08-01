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
    onPrimary = CrewTallyPalette.OnBlueContainer,
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
