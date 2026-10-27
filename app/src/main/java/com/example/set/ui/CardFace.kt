package com.example.set.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.set.engine.Card
import com.example.set.engine.CardShading
import com.example.set.ui.theme.Accent
import com.example.set.ui.theme.CardBorder
import com.example.set.ui.theme.CardFaceBackground
import com.example.set.ui.theme.Gold
import com.example.set.ui.theme.Ink
import com.example.set.ui.theme.cardColor

private val CardShape12 = RoundedCornerShape(12.dp)

/**
 * One Set card, drawn from geometry only — no image assets. Selection, hints and
 * the "set found" gold blink are expressed through the border as well as colour,
 * so they survive colour-vision differences.
 */
@Composable
fun CardFace(
    card: Card,
    colorblindPalette: Boolean,
    isSelected: Boolean,
    isHinted: Boolean,
    isGold: Boolean,
    animateDeal: Boolean,
    dealKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintTransition = rememberInfiniteTransition(label = "hint")
    val hintPulse by hintTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "hintPulse",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected || isGold) 1.02f else 1f,
        animationSpec = tween(120),
        label = "cardScale",
    )

    // Two gold blinks, then the ViewModel removes the set.
    val goldBlink = remember { Animatable(0f) }
    LaunchedEffect(isGold) {
        if (isGold) {
            repeat(2) {
                goldBlink.animateTo(1f, tween(150))
                goldBlink.animateTo(0f, tween(150))
            }
        } else {
            goldBlink.snapTo(0f)
        }
    }

    // New cards slide in from the bottom-right corner.
    val dealProgress = remember(card, dealKey) {
        Animatable(if (animateDeal) 0f else 1f)
    }
    LaunchedEffect(dealKey) {
        if (dealProgress.value < 1f) {
            dealProgress.animateTo(1f, tween(280))
        }
    }

    val borderColor = when {
        isGold -> Gold.copy(alpha = 0.30f + 0.70f * goldBlink.value)
        isHinted -> Accent.copy(alpha = hintPulse)
        isSelected -> Ink
        else -> CardBorder
    }
    val borderWidth = when {
        isGold || isHinted -> 3.dp
        isSelected -> 2.dp
        else -> 1.dp
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = (1f - dealProgress.value) * 56.dp.toPx()
                translationY = (1f - dealProgress.value) * 40.dp.toPx()
                alpha = 0.2f + 0.8f * dealProgress.value
            }
            .shadow(if (isSelected || isGold) 4.dp else 1.dp, CardShape12)
            .clip(CardShape12)
            .background(CardFaceBackground)
            .border(borderWidth, borderColor, CardShape12)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = card.description
                selected = isSelected
                role = Role.Button
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            val count = card.number.value
            val aspect = ShapePaths.aspect(card.shape) // width / height
            val gapRatio = 0.12f
            val maxShapeWidth = size.width * 0.94f
            val heightBudget = size.height * 0.88f

            // Shapes stack vertically, as on the physical cards.
            val perShapeHeight = heightBudget / (count + gapRatio * (count - 1))
            val shapeHeight = minOf(perShapeHeight, maxShapeWidth / aspect)
            val shapeWidth = shapeHeight * aspect
            val gap = shapeHeight * gapRatio

            val totalHeight = count * shapeHeight + (count - 1) * gap
            var top = (size.height - totalHeight) / 2f
            val left = (size.width - shapeWidth) / 2f

            val color = cardColor(card.color, colorblindPalette)
            val unitPath = ShapePaths.of(card.shape)
            val stroke = 2.2.dp.toPx().coerceAtMost(shapeWidth * 0.08f)

            repeat(count) {
                val path = scaledPath(unitPath, shapeWidth, shapeHeight, left, top)
                drawShaded(path, color, card.shading, stroke)
                top += shapeHeight + gap
            }
        }
    }
}

private fun scaledPath(
    unit: Path,
    width: Float,
    height: Float,
    left: Float,
    top: Float,
): Path = Path().apply {
    addPath(unit)
    transform(Matrix().apply { scale(width, height) })
    translate(Offset(left, top))
}

private fun DrawScope.drawShaded(
    path: Path,
    color: Color,
    shading: CardShading,
    strokeWidth: Float,
) {
    when (shading) {
        CardShading.SOLID -> drawPath(path, color, style = Fill)

        CardShading.OPEN -> drawPath(path, color, style = Stroke(width = strokeWidth))

        CardShading.STRIPED -> {
            clipPath(path) {
                val bounds = path.getBounds()
                val stripes = 9
                val slot = bounds.width / stripes
                val stripeWidth = slot * 0.42f
                for (i in 0 until stripes) {
                    val x = bounds.left + slot * (i + 0.5f)
                    drawLine(
                        color = color,
                        start = Offset(x, bounds.top - 2f),
                        end = Offset(x, bounds.bottom + 2f),
                        strokeWidth = stripeWidth,
                    )
                }
            }
            drawPath(path, color, style = Stroke(width = strokeWidth))
        }
    }
}
