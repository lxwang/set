package com.example.set.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckTest {
    @Test
    fun deckHas81UniqueCards() {
        val deck = fullDeck()
        assertEquals(81, deck.size)
        assertEquals(81, deck.toSet().size)
    }

    @Test
    fun everyAttributeValueAppears27Times() {
        val deck = fullDeck()
        CardNumber.entries.forEach { assertEquals(27, deck.count { c -> c.number == it }) }
        CardColor.entries.forEach { assertEquals(27, deck.count { c -> c.color == it }) }
        CardShape.entries.forEach { assertEquals(27, deck.count { c -> c.shape == it }) }
        CardShading.entries.forEach { assertEquals(27, deck.count { c -> c.shading == it }) }
    }

    @Test
    fun shuffledDeckIsAPermutation() {
        val shuffled = shuffledDeck(Random(42))
        assertEquals(81, shuffled.size)
        assertEquals(fullDeck().toSet(), shuffled.toSet())
    }

    @Test
    fun descriptionReadsNaturally() {
        val card = Card(CardNumber.TWO, CardColor.RED, CardShape.DIAMOND, CardShading.SOLID)
        assertEquals("two solid red diamonds", card.description)

        val single = Card(CardNumber.ONE, CardColor.PURPLE, CardShape.SQUIGGLE, CardShading.OPEN)
        assertEquals("one open purple squiggle", single.description)
    }
}
