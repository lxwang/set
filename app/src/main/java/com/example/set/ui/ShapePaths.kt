package com.example.set.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import com.example.set.engine.CardShape
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shape outlines normalised into a unit box (0,0)–(1,1), matching the physical
 * cards: every shape is wider than it is tall, and [aspect] gives that ratio.
 * The card renderer scales each path to the size it needs.
 */
object ShapePaths {

    /** Wide rhombus filling the box. */
    private val diamond: Path = Path().apply {
        moveTo(0f, 0.5f)
        lineTo(0.5f, 0f)
        lineTo(1f, 0.5f)
        lineTo(0.5f, 1f)
        close()
    }

    /** Wide rounded rectangle; corner radius stays below half the height. */
    private val oval: Path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1f,
                cornerRadius = CornerRadius(0.26f, 0.24f),
            ),
        )
    }

    /**
     * Horizontal squiggle: a band of varying thickness following a sine-shaped
     * centre line, filling the box. Sampled outline rather than hand-placed
     * Béziers, which keeps the curve organic at any size.
     */
    private val squiggle: Path = buildSquiggle()

    fun of(shape: CardShape): Path = when (shape) {
        CardShape.DIAMOND -> diamond
        CardShape.SQUIGGLE -> squiggle
        CardShape.OVAL -> oval
    }

    /** Width / height ratio, used to lay shapes out without distortion. */
    fun aspect(shape: CardShape): Float = when (shape) {
        CardShape.DIAMOND -> 1.60f
        CardShape.OVAL -> 1.75f
        CardShape.SQUIGGLE -> 1.55f
    }
}

private fun buildSquiggle(): Path {
    val steps = 64
    val amplitude = 0.10f
    val halfHeight = 0.35f
    val extent = amplitude + halfHeight

    fun centre(x: Float): Float = 0.5f + amplitude * sin(TWO_PI * x)

    fun halfHeightAt(x: Float): Float {
        // sin(PI) is a hair negative in Float; sqrt of that is NaN and would
        // blank the whole card. Clamp before taking the root.
        val taper = 0.65f + 0.35f * sqrt(sin(PI * x).coerceAtLeast(0f))
        return halfHeight * taper
    }

    fun y(x: Float, side: Float): Float {
        val offset = (centre(x) - 0.5f) + side * halfHeightAt(x)
        return 0.5f + offset / extent * 0.5f
    }

    return Path().apply {
        moveTo(0f, y(0f, -1f))
        for (i in 1..steps) {
            val x = i.toFloat() / steps
            lineTo(x, y(x, -1f))
        }
        for (i in steps downTo 0) {
            val x = i.toFloat() / steps
            lineTo(x, y(x, 1f))
        }
        close()
    }
}

private const val PI = kotlin.math.PI.toFloat()
private const val TWO_PI = 2f * PI
