package com.vague.crewtally.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The CrewTally brand mark (LOCKED 2026-09-21) wherever it appears on screen: the Home tab
 * header next to the app name, and the Reports hub title. One composable over one drawable
 * (`R.drawable.ic_brand_mark`), so size and semantics never drift between call sites.
 *
 * It is always DECORATIVE (`contentDescription = null`): every placement sits beside text that
 * already names the app or the screen, so labelling the mark would make TalkBack say the same
 * thing twice. It is drawn with [Image], not `Icon`, because the mark is two-colour by design
 * (grey frame + navy arrow) and `Icon`'s single tint would flatten it.
 */
@Composable
fun CrewTallyBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = CrewTallyTheme.dimens.iconLg,
) {
    Image(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
