package com.example.set.data

import com.example.set.engine.GameState
import com.example.set.engine.findSetIndices
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCodecTest {

    @Test
    fun roundTripKeepsAFreshGame() {
        val state = GameState.newGame(random = Random(9))
        val decoded = GameCodec.decode(GameCodec.encode(state))

        // dealtSlots is transient animation state, not persisted.
        assertEquals(state.copy(dealtSlots = emptyList()), decoded)
    }

    @Test
    fun roundTripKeepsHolesAndProgress() {
        var state = GameState.newGame(random = Random(21))
        repeat(5) {
            val indices = findSetIndices(state.table)!!
            state = indices.fold(state) { acc, index -> acc.tap(index) }.ensurePlayable()
        }
        state = state.copy(elapsedSeconds = 137, mistakes = 2, hintsUsed = 1, started = true)

        val decoded = GameCodec.decode(GameCodec.encode(state))
        assertEquals(state.copy(dealtSlots = emptyList()), decoded)
    }

    @Test
    fun roundTripKeepsAnEmptyDeckWithHoles() {
        // Build a late-game state: 12 slots, a set, no deck.
        var state = GameState.newGame(random = Random(4))
        while (state.deck.isNotEmpty()) {
            val indices = findSetIndices(state.table)!!
            state = indices.fold(state) { acc, index -> acc.tap(index) }.ensurePlayable()
        }
        val decoded = GameCodec.decode(GameCodec.encode(state))

        assertEquals(state.copy(dealtSlots = emptyList()), decoded)
        assertTrue(decoded!!.table.size >= GameState.TABLE_SIZE)
    }

    @Test
    fun rejectsGarbageAndInconsistentSaveFiles() {
        assertNull(GameCodec.decode(""))
        assertNull(GameCodec.decode("v2;mode=TIMED"))
        assertNull(GameCodec.decode("v1;mode=TIMED;sets=0;mistakes=0;hints=0;elapsed=0;started=0;extra=0;deals=0;table=0000;deck="))

        val valid = GameCodec.encode(GameState.newGame(random = Random(10)))
        // Corrupt one table card so the 81-card invariant breaks.
        val broken = valid.replaceFirst("table=", "table=9999,")
        assertNull(GameCodec.decode(broken))
    }
}
