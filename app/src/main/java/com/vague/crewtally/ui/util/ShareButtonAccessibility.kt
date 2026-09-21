package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R

/**
 * Why a share button ([com.vague.crewtally.ui.components.CrewTallyButton] with `enabled = false`)
 * is currently disabled. TalkBack only speaks a disabled button's visible label ("Share as
 * text") with no reason attached, so callers use [shareButtonDisabledDescription] to build a
 * contentDescription that also says why, e.g. "Share as text, disabled: no activity in the
 * selected date range."
 *
 * Kept as a plain enum + pure function (no stringResource) so the priority order between the
 * three reasons is unit-testable without Robolectric; [shareButtonDisabledDescription] is the
 * resource-resolving half a screen calls per button.
 */
enum class ShareButtonDisabledReason {
    GENERATING,
    RANGE_INVALID,
    NO_CONTENT,
}

/**
 * Picks which single reason to report when more than one could apply. Generating is checked
 * first (it is transient and about to resolve on its own); an invalid range next (it makes
 * "hasContent" meaningless until fixed); "nothing in range" last.
 */
fun shareButtonDisabledReason(
    isGenerating: Boolean,
    isRangeInvalid: Boolean,
    hasContent: Boolean,
): ShareButtonDisabledReason? = when {
    isGenerating -> ShareButtonDisabledReason.GENERATING
    isRangeInvalid -> ShareButtonDisabledReason.RANGE_INVALID
    !hasContent -> ShareButtonDisabledReason.NO_CONTENT
    else -> null
}

/**
 * The full accessible label for a share button: just [label] when it is enabled, or [label] plus
 * the spoken reason when it is disabled. [noContentReasonText] is passed in already resolved
 * rather than looked up here, because "nothing to share" has different wording per bucket/context
 * (see `emptyMessage` in `ClerkMultiProjectStatementShareScreen`) — this helper only owns the
 * shared "label, disabled: reason" assembly and the generating/range-invalid wording, which are
 * the same everywhere.
 */
@Composable
fun shareButtonDisabledDescription(
    label: String,
    isGenerating: Boolean,
    isRangeInvalid: Boolean,
    hasContent: Boolean,
    noContentReasonText: String,
): String {
    val reason = shareButtonDisabledReason(isGenerating, isRangeInvalid, hasContent) ?: return label
    val reasonText = when (reason) {
        ShareButtonDisabledReason.GENERATING -> stringResource(R.string.report_preparing)
        ShareButtonDisabledReason.RANGE_INVALID -> stringResource(R.string.report_range_invalid)
        ShareButtonDisabledReason.NO_CONTENT -> noContentReasonText
    }
    return stringResource(R.string.cd_share_button_disabled, label, reasonText)
}
