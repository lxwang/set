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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.set.engine.Card
import com.example.set.engine.CardShading
import com.example.set.ui.theme.Accent
import com.example.set.ui.theme.Gold
import com.example.set.ui.theme.cardColor
import kotlinx.coroutines.delay

private val CardShape12 = RoundedCornerShape(12.dp)

/**
 * One Set card, drawn from geometry only — no image assets. Selection, hints,
 * the invalid shake and the "set found" gold pop are expressed through shape and
 * border as well as colour, so they survive colour-vision differences.
 *
 * The deal animation lives in [DealIn] because it needs the card's position.
 */
@Composable
fun CardFace(
    card: Card,
    colorblindPalette: Boolean,
    isSelected: Boolean,
    isHinted: Boolean,
    isGold: Boolean,
    isInvalid: Boolean,
    shakeKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.background.luminance() < 0.5f

    val hintTransition = rememberInfiniteTransition(label = "hint")
    val hintPulse by hintTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "hintPulse",
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = tween(120),
        label = "cardScale",
    )

    // A found set turns gold immediately, then pops 6% larger before removal.
    val goldPop = remember { Animatable(1f) }
    LaunchedEffect(isGold) {
        if (isGold) {
            delay(200)
            goldPop.animateTo(1.06f, tween(140))
        } else {
            goldPop.snapTo(1f)
        }
    }

    // Invalid trio: shake these three cards only, never the board.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey, isInvalid) {
        if (isInvalid) {
            repeat(3) {
                shake.animateTo(6f, tween(50))
                shake.animateTo(-6f, tween(50))
            }
            shake.animateTo(0f, tween(50))
        }
    }

    val borderColor = when {
        isGold -> Gold
        isHinted -> Accent.copy(alpha = hintPulse)
        isSelected -> scheme.onSurface
        else -> scheme.outlineVariant
    }
    val borderWidth = when {
        isGold || isHinted -> 3.dp
        isSelected -> 2.dp
        else -> 1.dp
    }
    val scale = if (isGold) goldPop.value else selectedScale

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shake.value.dp.toPx()
            }
            .shadow(if (isSelected || isGold) 4.dp else 1.dp, CardShape12)
            .clip(CardShape12)
            .background(scheme.surfaceContainerLowest)
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
            val gapRatio = 0.10f
            val maxShapeWidth = size.width * 0.94f
            val heightBudget = size.height * 0.94f

            // Symbol size shrinks deliberately with the count: 1 -> 1.0,
            // 2 -> 0.9, 3 -> 0.8 of the single-symbol size. The base size is
            // solved from the three-symbol case so that column still fits.
            val scaleFactor = when (count) {
                1 -> 1f
                2 -> 0.9f
                else -> 0.8f
            }
            val baseHeight = minOf(
                maxShapeWidth / aspect,
                heightBudget / (3f * 0.8f + 2f * gapRatio * 0.8f),
            )

            // Shapes stack vertically, as on the physical cards.
            val shapeHeight = baseHeight * scaleFactor
            val shapeWidth = shapeHeight * aspect
            val gap = shapeHeight * gapRatio

            val totalHeight = count * shapeHeight + (count - 1) * gap
            var top = (size.height - totalHeight) / 2f
            val left = (size.width - shapeWidth) / 2f

            val color = cardColor(card.color, colorblindPalette, darkTheme)
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
