package com.example.set.engine

import kotlin.random.Random

enum class GamePhase { PLAYING, PAUSED, GAME_OVER }

enum class GameMode { TIMED, RELAXED }

/**
 * Immutable game state. Every transition is a pure function returning a new
 * state, which keeps the rules testable without Android or a clock.
 *
 * Invariant after every transition:
 *   table.size + deck.size + 3 * setsFound == 81
 */
data class GameState(
    val table: List<Card> = emptyList(),
    val deck: List<Card> = emptyList(),
    val selected: List<Int> = emptyList(),
    val setsFound: Int = 0,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val phase: GamePhase = GamePhase.PLAYING,
    val mode: GameMode = GameMode.TIMED,
    val elapsedSeconds: Long = 0,
    /** The clock starts on the first tap (or hint), not on the deal. */
    val started: Boolean = false,
    /** Indices currently pulsing as a hint, or null. */
    val hintIndices: List<Int>? = null,
    /** Number of extra-deal events so far; the UI flashes a banner on change. */
    val extraDeals: Int = 0,
) {
    val hintsRemaining: Int get() = (MAX_HINTS - hintsUsed).coerceAtLeast(0)

    /**
     * Taps a card on the table. Resolving the third selection may remove the
     * set and draw replacements. It does not deal extra cards for a set-free
     * table — call [ensurePlayable] for that.
     */
    fun tap(index: Int): GameState {
        if (phase != GamePhase.PLAYING) return this
        if (index !in table.indices) return this

        val nextSelected = when {
            index in selected -> selected - index
            selected.size == 3 -> return this // wait for the current trio to resolve
            else -> selected + index
        }

        if (nextSelected.size < 3) {
            return copy(selected = nextSelected, started = true, hintIndices = null)
        }

        val trio = nextSelected.map { table[it] }
        if (!isSet(trio[0], trio[1], trio[2])) {
            return copy(
                selected = emptyList(),
                mistakes = mistakes + 1,
                started = true,
                hintIndices = null,
            )
        }

        val remaining = table.filterIndexed { i, _ -> i !in nextSelected }
        // Replacements only bring the table back up to TABLE_SIZE. While the
        // table is enlarged (15/18), a found set is removed without dealing.
        val dealCount =
            if (deck.isNotEmpty() && remaining.size < TABLE_SIZE) {
                minOf(TABLE_SIZE - remaining.size, deck.size)
            } else {
                0
            }

        return copy(
            table = remaining + deck.take(dealCount),
            deck = deck.drop(dealCount),
            selected = emptyList(),
            setsFound = setsFound + 1,
            started = true,
            hintIndices = null,
        )
    }

    /**
     * Deals three extra cards while the table has no set, as the rules require.
     * Ends the game when the deck is empty and no set remains.
     */
    fun ensurePlayable(): GameState {
        if (phase != GamePhase.PLAYING) return this

        var playableTable = table
        var remainingDeck = deck
        var deals = 0

        while (findSetIndices(playableTable) == null) {
            if (remainingDeck.isEmpty()) {
                return copy(
                    table = playableTable,
                    deck = remainingDeck,
                    selected = emptyList(),
                    phase = GamePhase.GAME_OVER,
                    hintIndices = null,
                    extraDeals = extraDeals + if (deals > 0) 1 else 0,
                )
            }
            playableTable = playableTable + remainingDeck.take(3)
            remainingDeck = remainingDeck.drop(3)
            deals++
        }

        if (deals == 0) return this
        return copy(
            table = playableTable,
            deck = remainingDeck,
            selected = emptyList(),
            hintIndices = null,
            extraDeals = extraDeals + deals,
        )
    }

    /** Reveals one valid set. Costs one of the three hints per game. */
    fun hint(): GameState {
        if (phase != GamePhase.PLAYING) return this
        if (hintsRemaining == 0) return this
        val indices = findSetIndices(table) ?: return this
        return copy(
            hintsUsed = hintsUsed + 1,
            hintIndices = indices,
            started = true,
        )
    }

    fun clearHint(): GameState = if (hintIndices == null) this else copy(hintIndices = null)

    fun pause(): GameState =
        if (phase == GamePhase.PLAYING) copy(phase = GamePhase.PAUSED) else this

    fun resume(): GameState =
        if (phase == GamePhase.PAUSED) copy(phase = GamePhase.PLAYING) else this

    fun withElapsed(seconds: Long): GameState =
        if (seconds == elapsedSeconds) this else copy(elapsedSeconds = seconds)

    fun selectedCard(index: Int): Card? = table.getOrNull(index)

    companion object {
        const val TABLE_SIZE = 12
        const val TOTAL_SETS = 27

        fun newGame(mode: GameMode = GameMode.TIMED, random: Random = Random.Default): GameState {
            val deck = shuffledDeck(random)
            return GameState(
                table = deck.take(TABLE_SIZE),
                deck = deck.drop(TABLE_SIZE),
                mode = mode,
            ).ensurePlayable()
        }
    }
}
