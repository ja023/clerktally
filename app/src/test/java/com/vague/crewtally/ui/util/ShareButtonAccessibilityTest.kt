package com.vague.crewtally.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure priority-ordering half of [shareButtonDisabledDescription] — see its KDoc. */
class ShareButtonAccessibilityTest {

    @Test
    fun `returns null when nothing disables the button`() {
        assertNull(shareButtonDisabledReason(isGenerating = false, isRangeInvalid = false, hasContent = true))
    }

    @Test
    fun `generating wins over an invalid range and no content`() {
        assertEquals(
            ShareButtonDisabledReason.GENERATING,
            shareButtonDisabledReason(isGenerating = true, isRangeInvalid = true, hasContent = false),
        )
    }

    @Test
    fun `an invalid range wins over no content when not generating`() {
        assertEquals(
            ShareButtonDisabledReason.RANGE_INVALID,
            shareButtonDisabledReason(isGenerating = false, isRangeInvalid = true, hasContent = false),
        )
    }

    @Test
    fun `no content is reported only when generating and range are both fine`() {
        assertEquals(
            ShareButtonDisabledReason.NO_CONTENT,
            shareButtonDisabledReason(isGenerating = false, isRangeInvalid = false, hasContent = false),
        )
    }
}
