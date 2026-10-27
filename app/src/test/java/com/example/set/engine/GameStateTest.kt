package com.example.set.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {
    private fun assertInvariant(state: GameState) {
        assertEquals(81, state.cardsOnTable + state.deck.size + 3 * state.setsFound)
        val cards = state.table.filterNotNull()
        assertEquals(cards.size, cards.distinct().size)
        assertTrue(state.selected.size <= 3)
    }

    @Test
    fun newGameIsPlayableAndConsistent() {
        for (seed in 1..50) {
            val state = GameState.newGame(random = Random(seed))
            assertInvariant(state)
            assertEquals(GamePhase.PLAYING, state.phase)
            assertTrue("table size ${state.table.size}", state.table.size in 12..18)
            assertEquals(state.table.size, state.cardsOnTable)
            assertNotNull("seed $seed dealt a set-free table", findSetIndices(state.table))
            assertEquals(GameMode.TIMED, state.mode)
            assertFalse(state.started)
        }
    }

    @Test
    fun playthroughClearsTheWholeDeck() {
        var state = GameState.newGame(random = Random(1234))
        var guard = 0
        while (state.phase == GamePhase.PLAYING && guard++ < 200) {
            val indices = findSetIndices(state.table) ?: error("playing but no set on the table")
            state = indices.fold(state) { acc, index -> acc.tap(index) }
            state = state.ensurePlayable()
            assertInvariant(state)
        }
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertTrue(state.deck.isEmpty())
        // Leftover cards are legal: a 12-slot table can end with 0, 3, 6 or 9
        // cards and no set among them.
        assertEquals(81 - 3 * state.setsFound, state.cardsOnTable)
        assertNull("a finished table must be set-free", findSetIndices(state.table))
        assertTrue("setsFound ${state.setsFound}", state.setsFound in 23..27)
        assertEquals(0, state.mistakes)
    }

    @Test
    fun invalidTrioChargesAMistakeAndKeepsTheTable() {
        val state = GameState(table = capSetCards(12), deck = fullDeck().take(3))
        val after = listOf(0, 1, 2).fold(state) { acc, index -> acc.tap(index) }

        assertEquals(1, after.mistakes)
        assertEquals(0, after.setsFound)
        assertTrue(after.selected.isEmpty())
        assertEquals(state.table, after.table)
        assertEquals(state.deck, after.deck)
        assertTrue(after.started)
    }

    @Test
    fun replacementsFillTheVacatedSlotsWithoutMovingOtherCards() {
        val untouched = capSetCards(9)
        val table = untouched + ovalTrio()
        val replacements = listOf(
            Card(CardNumber.THREE, CardColor.RED, CardShape.OVAL, CardShading.STRIPED),
            Card(CardNumber.THREE, CardColor.GREEN, CardShape.OVAL, CardShading.SOLID),
            Card(CardNumber.THREE, CardColor.PURPLE, CardShape.OVAL, CardShading.STRIPED),
        )
        val state = GameState(table = table, deck = replacements)
        val after = listOf(9, 10, 11).fold(state) { acc, index -> acc.tap(index) }

        assertEquals(1, after.setsFound)
        assertEquals(12, after.cardsOnTable)
        assertTrue(after.deck.isEmpty())
        // Everything outside the trio stayed exactly where it was.
        assertEquals(untouched, after.table.take(9))
        // The new cards went into the three vacated slots, in order.
        assertEquals(replacements, after.table.subList(9, 12))
        assertEquals(listOf(9, 10, 11), after.dealtSlots)
        assertEquals(1, after.deals)
    }

    @Test
    fun removedSetLeavesHolesWhenTheDeckIsEmpty() {
        val table = capSetCards(9) + ovalTrio()
        // 12 cards on the table and an empty deck means 23 sets are already gone.
        val state = GameState(table = table, deck = emptyList(), setsFound = 23)
        val after = listOf(9, 10, 11).fold(state) { acc, index -> acc.tap(index) }

        assertEquals(24, after.setsFound)
        assertEquals(9, after.cardsOnTable)
        assertEquals(12, after.table.size) // the grid keeps its shape
        assertNull(after.table[9])
        assertNull(after.table[10])
        assertNull(after.table[11])
        assertTrue(after.dealtSlots.isEmpty())
        assertEquals(capSetCards(9), after.table.take(9))
        assertInvariant(after)
    }

    @Test
    fun findingASetOnAWidenedTableCompactsBackTowardTwelve() {
        val twelve = capSetCards(12)
        val table = twelve + ovalTrio()
        val deck = fullDeck().take(3)
        val state = GameState(table = table, deck = deck)
        val after = listOf(12, 13, 14).fold(state) { acc, index -> acc.tap(index) }

        assertEquals(1, after.setsFound)
        assertEquals(12, after.table.size)
        assertEquals(twelve, after.table)
        assertEquals(deck, after.deck)
        assertTrue(after.dealtSlots.isEmpty())
    }

    @Test
    fun ensurePlayableDealsThreeExtraWhenTheTableHasNoSet() {
        val state = GameState(table = capSetCards(12), deck = ovalTrio())
        val after = state.ensurePlayable()

        assertEquals(15, after.table.size)
        assertTrue(after.deck.isEmpty())
        assertEquals(1, after.extraDeals)
        assertEquals((12 until 15).toList(), after.dealtSlots)
        assertEquals(1, after.deals)
        assertNotNull(findSetIndices(after.table))
    }

    @Test
    fun ensurePlayableEndsTheGameWhenTheDeckIsEmpty() {
        val state = GameState(table = capSetCards(9), deck = emptyList(), setsFound = 24)
        val after = state.ensurePlayable()

        assertEquals(GamePhase.GAME_OVER, after.phase)
        assertTrue(after.selected.isEmpty())
        assertEquals(24, after.setsFound)
    }

    @Test
    fun hintsAreCappedAtThreeAndRevealRealSets() {
        var state = GameState.newGame(random = Random(3))
        repeat(3) {
            state = state.hint()
            val indices = state.hintIndices ?: error("a granted hint must mark a set")
            val revealed = indices.map { state.table[it]!! }
            assertTrue(isSet(revealed[0], revealed[1], revealed[2]))
        }
        assertEquals(3, state.hintsUsed)
        assertEquals(0, state.hintsRemaining)

        val afterFourth = state.hint()
        assertEquals(3, afterFourth.hintsUsed)
        assertEquals(state, afterFourth)
    }

    @Test
    fun pausedGameIgnoresInput() {
        val state = GameState.newGame(random = Random(1)).pause()
        assertEquals(GamePhase.PAUSED, state.phase)
        assertEquals(state, state.tap(0))
        assertEquals(state, state.hint())
        assertEquals(GamePhase.PLAYING, state.resume().phase)
    }

    @Test
    fun outOfRangeAndHoleTapsAreIgnored() {
        val withHole = GameState(table = listOf(null), deck = emptyList())
        assertEquals(withHole, withHole.tap(0))

        val state = GameState.newGame(random = Random(2))
        assertEquals(state, state.tap(-1))
        assertEquals(state, state.tap(state.table.size))
    }

    @Test
    fun tapsAreIgnoredWhileASetIsBlinking() {
        val state = GameState.newGame(random = Random(6)).copy(goldSlots = listOf(0, 1, 2))
        assertEquals(state, state.tap(3))
    }

    @Test
    fun penaltiesApplyOnlyToTimedGames() {
        val timed = GameState(mistakes = 3, elapsedSeconds = 60, mode = GameMode.TIMED)
        assertEquals(30, timed.penaltySeconds)
        assertEquals(90, timed.totalSeconds)

        val relaxed = timed.copy(mode = GameMode.RELAXED)
        assertEquals(0, relaxed.penaltySeconds)
        assertEquals(60, relaxed.totalSeconds)
    }

    @Test
    fun durationFormattingIsStable() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5))
        assertEquals("2:07", formatDuration(127))
        assertEquals("10:00", formatDuration(600))
        assertEquals("0:00", formatDuration(-3))
    }

    @Test
    fun selectingAnAlreadySelectedCardDeselectsIt() {
        val state = GameState.newGame(random = Random(4))
        val selected = state.tap(0).tap(1).tap(0)
        assertEquals(listOf(1), selected.selected)
        assertEquals(0, selected.mistakes)
    }

    @Test
    fun clearHintRemovesThePulse() {
        val hinted = GameState.newGame(random = Random(5)).hint()
        assertNotNull(hinted.hintIndices)
        assertNull(hinted.clearHint().hintIndices)
    }
}
