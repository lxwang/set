package com.example.set.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Slides a newly dealt card in from a deck imagined just off the bottom-left
 * corner of the screen. Every card converges from the same off-screen point, so
 * the board looks like it is being dealt from one pile.
 *
 * The translation is computed from the card's real window position, which keeps
 * it correct across screen sizes and whenever the board layout changes.
 */
private const val SLIDE_MILLIS = 360

@Composable
fun DealIn(
    animate: Boolean,
    dealKey: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    var positioned by remember { mutableStateOf(false) }

    val progress = remember(dealKey) { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(dealKey, animate, positioned, windowSize) {
        if (!animate) {
            // dealtSlots is retired on a timer. If that lands while the card is
            // still flying, finish the slide instead of freezing it in mid-air.
            if (progress.value < 1f) progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (positioned && progress.value < 1f) {
            progress.animateTo(1f, tween(SLIDE_MILLIS))
        }
    }

    val corner = Offset(-120f, windowSize.height + 120f)

    Box(
        // Lift the flying card above its row siblings while it slides in.
        modifier = modifier
            .zIndex(if (animate) 1f else 0f)
            .onGloballyPositioned {
                topLeft = it.positionInWindow()
                positioned = true
            }
            .graphicsLayer {
                val remaining = 1f - progress.value
                if (remaining > 0f && positioned) {
                    translationX = (corner.x - topLeft.x) * remaining
                    translationY = (corner.y - topLeft.y) * remaining
                }
            },
    ) {
        content()
    }
}
