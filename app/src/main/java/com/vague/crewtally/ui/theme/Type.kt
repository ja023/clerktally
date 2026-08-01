package com.vague.crewtally.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * CrewTally type scale. The primary user is ~55 years old and often reads outdoors, so
 * this scale is deliberately LARGE and heavy, on top of full system dynamic-type support:
 *
 * - Body text is never below 16sp (`bodyLarge` / `bodyMedium`).
 * - List-item primary text is >= 18sp (`titleMedium`), the size shared components use.
 * - Money (rates, balances) uses the dedicated [CrewTallyType] number styles: large and
 *   bold so an amount is legible at a glance and never mistaken for body copy.
 *
 * System font family (FontFamily.Default) is used intentionally: it already respects the
 * user's device font and accessibility settings, and keeps the offline APK small.
 */
private val Sans = FontFamily.Default

val CrewTallyTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    // Title = the workhorse for list rows and section headers. Kept >= 18sp on purpose.
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    // Body never below 16sp.
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    // Labels (buttons, tabs). Button label is >= 18sp; see CrewTallyButton.
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

/**
 * Money + emphasis text styles that don't map onto a Material slot. Read via
 * `CrewTallyType.moneyLarge` etc. Numbers are rendered large and bold so a rate or
 * balance is the loudest thing in a row. Tabular figures keep columns of amounts aligned.
 */
object CrewTallyType {
    /** Hero balance / total on a detail or dashboard screen. */
    val moneyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        textAlign = TextAlign.End,
    )

    /** Money inside a list row (a clerk's balance, a project total). */
    val moneyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        textAlign = TextAlign.End,
    )

    /** Secondary amounts (an extra-pay line, a single payment). */
    val moneySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        textAlign = TextAlign.End,
    )
}
