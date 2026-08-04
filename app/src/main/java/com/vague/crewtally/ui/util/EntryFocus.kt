package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * The single wizard-step focus pattern (Phase 6 review-debt paydown).
 *
 * Returns a [FocusRequester] that pulls input focus — and therefore the TalkBack
 * accessibility cursor — onto the field it is attached to the moment its composable enters
 * composition. Because each wizard step's content enters a fresh composition when the step
 * becomes active, dropping this on the step's first field is all it takes to move focus onto
 * the new step instead of leaving the cursor stranded on the previous one.
 *
 * The liveRegion step-title announcement (which speaks the new step's name) stays as the
 * signal for the multi-select list steps, where auto-focusing the search field would pop the
 * soft keyboard over the very list the user is trying to scan — a worse trade for the ~55-year-
 * old primary user. This helper is therefore attached only to the text-entry steps, where
 * landing ready-to-type is exactly what the user wants. Text-entry steps that use this must
 * also suppress their title's liveRegion with [wizardStepTitleSemantics] (`announceTitle =
 * false`) and fold the step name into the focused field's label with [entryFocusFieldLabel] —
 * the title announcement and the focus-move TalkBack performs when focus lands race each
 * other, and pairing both drops one.
 *
 * MUST be called at the wizard step's top-level composable, never inside a `LazyColumn`/
 * `LazyRow` item scope. A lazy list tears down and rebuilds an off-screen item's composition
 * as it scrolls out of and back into the visible window; a `remember`/`LaunchedEffect(Unit)`
 * placed inside the item would then refire every time row 0 re-enters view, stealing focus
 * (and the keyboard) from whatever field the user is currently typing in. Hoist the requester
 * once above the list and attach it only to the first row's field instead (see
 * [com.vague.crewtally.ui.screen.CreateProjectRatesStep]).
 *
 * Attach with `Modifier.focusRequester(rememberEntryFocusRequester())` or pass it to a
 * [com.vague.crewtally.ui.components.CrewTallyTextField]'s `focusRequester` parameter. The
 * request runs inside a try/catch for [IllegalStateException] — the specific failure a
 * [FocusRequester] throws when `requestFocus()` runs before it is attached to any composable —
 * so that off-frame race degrades to "no focus move" rather than a crash.
 */
@Composable
fun rememberEntryFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            requester.requestFocus()
        } catch (_: IllegalStateException) {
            // Not yet attached to a composable this frame — no focus move, no crash.
        }
    }
    return requester
}

/**
 * TopAppBar title semantics for one wizard step. List/multi-select steps should always
 * announce their title via a Polite liveRegion so TalkBack picks up the step change
 * (`announceTitle = true`). Text-entry steps that auto-focus their first field with
 * [rememberEntryFocusRequester] must NOT also announce the title this way — the liveRegion
 * announcement and the focus-move TalkBack performs when focus lands race each other, and the
 * step-title announcement gets dropped. Those steps pass `announceTitle = false` here and use
 * [entryFocusFieldLabel] on the focused field instead, so TalkBack speaks one coherent
 * utterance (e.g. "Set daily rates. Jad Awad daily rate, edit box") rather than two competing
 * ones.
 */
fun Modifier.wizardStepTitleSemantics(announceTitle: Boolean): Modifier = semantics {
    heading()
    if (announceTitle) liveRegion = LiveRegionMode.Polite
}

/**
 * Folds a wizard step's title into an auto-focused field's accessible label, e.g.
 * `entryFocusFieldLabel("Set daily rates", "Jad Awad")` -> `"Set daily rates. Jad Awad"`. Pair
 * with [wizardStepTitleSemantics] (`announceTitle = false`) on the step's TopAppBar title — see
 * that doc for why the two must move together.
 */
fun entryFocusFieldLabel(stepTitle: String, fieldLabel: String): String = "$stepTitle. $fieldLabel"
