package com.example.set.engine

import kotlin.random.Random

enum class GamePhase { PLAYING, PAUSED, GAME_OVER }

enum class GameMode { TIMED, RELAXED }

/**
 * Immutable game state. Every transition is a pure function returning a new
 * state, which keeps the rules testable without Android or a clock.
 *
 * The table is a list of *slots*: a null slot is a hole left by a removed set.
 * Holes are filled in place from the deck, so the remaining cards never move.
 *
 * Invariant after every transition:
 *   cardsOnTable + deck.size + 3 * setsFound == 81
 */
data class GameState(
    val table: List<Card?> = emptyList(),
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
    /** A completed set waiting for its blink animation before it is removed. */
    val goldSlots: List<Int> = emptyList(),
    /** Slots filled by the most recent deal, for the slide-in animation. */
    val dealtSlots: List<Int> = emptyList(),
    /** Monotonic counter, bumped once per deal event; keys the deal animation. */
    val deals: Int = 0,
) {
    val hintsRemaining: Int get() = (MAX_HINTS - hintsUsed).coerceAtLeast(0)

    val cardsOnTable: Int get() = table.count { it != null }

    val isResolving: Boolean get() = goldSlots.isNotEmpty()

    fun cardAt(index: Int): Card? = table.getOrNull(index)

    /**
     * Taps a slot. Resolving the third selection may remove the set and draw
     * replacements. It does not deal extra cards for a set-free table — call
     * [ensurePlayable] for that.
     */
    fun tap(index: Int): GameState {
        if (phase != GamePhase.PLAYING) return this
        if (index !in table.indices) return this
        if (table[index] == null) return this
        if (isResolving) return this

        val nextSelected = when {
            index in selected -> selected - index
            selected.size == 3 -> return this // wait for the current trio to resolve
            else -> selected + index
        }

        if (nextSelected.size < 3) {
            return copy(selected = nextSelected, started = true, hintIndices = null)
        }

        val trio = nextSelected.map { table[it]!! }
        if (!isSet(trio[0], trio[1], trio[2])) {
            return copy(
                selected = emptyList(),
                mistakes = mistakes + 1,
                started = true,
                hintIndices = null,
            )
        }

        // Leave holes where the set was; nothing else moves.
        val holes = table.mapIndexed { i, card -> if (i in nextSelected) null else card }
        var newTable = holes
        var newDeck = deck
        var dealt = emptyList<Int>()

        if (holes.count { it != null } >= TABLE_SIZE) {
            // The table was widened to 15/18 by an extra deal. A found set takes
            // it back toward 12, so drop the holes instead of leaving gaps.
            newTable = holes.filterNotNull()
        } else if (newDeck.isNotEmpty()) {
            // Draw into the vacated slots, left to right, keeping every other
            // card exactly where it is.
            val open = TABLE_SIZE - holes.count { it != null }
            val take = minOf(open, newDeck.size)
            val builder = holes.toMutableList()
            var filled = 0
            for (slot in builder.indices) {
                if (filled == take) break
                if (builder[slot] == null) {
                    builder[slot] = newDeck[filled]
                    dealt = dealt + slot
                    filled++
                }
            }
            newTable = builder
            newDeck = newDeck.drop(take)
        }

        return copy(
            table = newTable,
            deck = newDeck,
            selected = emptyList(),
            setsFound = setsFound + 1,
            started = true,
            hintIndices = null,
            goldSlots = emptyList(),
            dealtSlots = dealt,
            deals = deals + if (dealt.isNotEmpty()) 1 else 0,
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
        var eventDeals = 0
        var dealt = emptyList<Int>()

        while (findSetIndices(playableTable) == null) {
            if (remainingDeck.isEmpty()) {
                return copy(
                    table = playableTable,
                    deck = remainingDeck,
                    selected = emptyList(),
                    phase = GamePhase.GAME_OVER,
                    hintIndices = null,
                    goldSlots = emptyList(),
                    dealtSlots = emptyList(),
                )
            }
            val take = remainingDeck.take(3)
            val firstNew = playableTable.size
            playableTable = playableTable + take
            dealt = dealt + (firstNew until playableTable.size)
            remainingDeck = remainingDeck.drop(take.size)
            eventDeals++
        }

        if (eventDeals == 0) return this
        return copy(
            table = playableTable,
            deck = remainingDeck,
            selected = emptyList(),
            hintIndices = null,
            extraDeals = extraDeals + eventDeals,
            dealtSlots = dealt,
            deals = deals + 1,
        )
    }

    /** Reveals one valid set. Costs one of the three hints per game. */
    fun hint(): GameState {
        if (phase != GamePhase.PLAYING) return this
        if (isResolving) return this
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
