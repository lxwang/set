package com.example.set.engine

const val MAX_HINTS = 3
const val MISTAKE_PENALTY_SECONDS = 10L

// GameState exposes `hintsRemaining` itself; the cap lives above.

/** Only timed games are penalised; relaxed games report raw elapsed time. */
val GameState.penaltySeconds: Long
    get() = if (mode == GameMode.TIMED) mistakes * MISTAKE_PENALTY_SECONDS else 0

val GameState.totalSeconds: Long
    get() = elapsedSeconds + penaltySeconds

/** "2:07", or "0:05". Minutes are not zero-padded. */
fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}
