package com.example.set.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import com.example.set.engine.CardShape
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shape outlines normalised into a unit box (0,0)–(1,1). The card renderer
 * scales each path to the size it needs, so these constants are the only place
 * geometry lives.
 */
object ShapePaths {

    /** Tall rhombus, narrower than the box so it doesn't read as a rotated square. */
    private val diamond: Path = Path().apply {
        moveTo(0.50f, 0.02f)
        lineTo(0.81f, 0.50f)
        lineTo(0.50f, 0.98f)
        lineTo(0.19f, 0.50f)
        close()
    }

    /**
     * Tall rounded rectangle. The corner radius must stay well below half the
     * width, otherwise the shape degenerates into an ellipse.
     */
    private val oval: Path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0.11f,
                top = 0.02f,
                right = 0.89f,
                bottom = 0.98f,
                cornerRadius = CornerRadius(0.32f, 0.36f),
            ),
        )
    }

    /**
     * The Set squiggle: a band of varying width following a sine-shaped centre
     * line. Sampling the outline is far easier to keep organic than hand-placed
     * Béziers, and the shape is built once.
     */
    private val squiggle: Path = buildSquiggle()

    fun of(shape: CardShape): Path = when (shape) {
        CardShape.DIAMOND -> diamond
        CardShape.SQUIGGLE -> squiggle
        CardShape.OVAL -> oval
    }

    /** Width / height ratio used to lay shapes out without distortion. */
    fun aspect(shape: CardShape): Float = when (shape) {
        CardShape.DIAMOND -> 0.62f
        CardShape.OVAL -> 0.78f
        CardShape.SQUIGGLE -> 0.82f
    }
}

private fun buildSquiggle(): Path {
    val steps = 64
    val yStart = 0.05f
    val yEnd = 0.95f
    val amplitude = 0.13f
    val halfWidth = 0.21f
    val extent = halfWidth + amplitude

    fun centre(y: Float): Float = 0.5f + amplitude * sin(TWO_PI * y)

    fun halfWidthAt(y: Float): Float {
        val t = (y - yStart) / (yEnd - yStart)
        // sin(PI) is a hair negative in Float; sqrt of that is NaN and would
        // poison the whole path (a blank card). Clamp before taking the root.
        val taper = 0.55f + 0.45f * sqrt(sin(PI * t).coerceAtLeast(0f))
        return halfWidth * taper
    }

    fun x(y: Float, side: Float): Float {
        val offset = (centre(y) - 0.5f) + side * halfWidthAt(y)
        return 0.5f + offset / extent * 0.5f
    }

    return Path().apply {
        moveTo(x(yStart, -1f), yStart)
        for (i in 1..steps) {
            val y = yStart + (yEnd - yStart) * i / steps
            lineTo(x(y, -1f), y)
        }
        for (i in steps downTo 0) {
            val y = yStart + (yEnd - yStart) * i / steps
            lineTo(x(y, 1f), y)
        }
        close()
    }
}

private const val PI = kotlin.math.PI.toFloat()
private const val TWO_PI = 2f * PI
