package com.example.set.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val oneRedDiamond = Card(CardNumber.ONE, CardColor.RED, CardShape.DIAMOND, CardShading.SOLID)

    @Test
    fun sameOnThreeAttributesDifferentOnOne_isASet() {
        val a = oneRedDiamond
        val b = a.copy(shading = CardShading.STRIPED)
        val c = a.copy(shading = CardShading.OPEN)
        assertTrue(isSet(a, b, c))
    }

    @Test
    fun allDifferentOnEveryAttribute_isASet() {
        val a = oneRedDiamond
        val b = Card(CardNumber.TWO, CardColor.GREEN, CardShape.SQUIGGLE, CardShading.STRIPED)
        val c = Card(CardNumber.THREE, CardColor.PURPLE, CardShape.OVAL, CardShading.OPEN)
        assertTrue(isSet(a, b, c))
    }

    @Test
    fun twoSameOneDifferent_neverIsASet() {
        val valid = Card(CardNumber.THREE, CardColor.PURPLE, CardShape.OVAL, CardShading.OPEN)
        // Break one attribute at a time by replacing one card with a value that
        // makes that attribute "two same, one different".
        val pairs = listOf(
            oneRedDiamond to Card(CardNumber.ONE, CardColor.GREEN, CardShape.SQUIGGLE, CardShading.STRIPED),
            oneRedDiamond to Card(CardNumber.TWO, CardColor.RED, CardShape.SQUIGGLE, CardShading.STRIPED),
            oneRedDiamond to Card(CardNumber.TWO, CardColor.GREEN, CardShape.DIAMOND, CardShading.STRIPED),
            oneRedDiamond to Card(CardNumber.TWO, CardColor.GREEN, CardShape.SQUIGGLE, CardShading.SOLID),
        )
        pairs.forEach { (a, b) -> assertFalse("expected not a set: $a $b $valid", isSet(a, b, valid)) }
    }

    /**
     * The single most valuable test in the suite: over all 85,320 triples in the
     * full deck, exactly 1,080 are sets. Any error in the rule almost certainly
     * changes this number.
     */
    @Test
    fun fullDeckContainsExactly1080Sets() {
        val deck = fullDeck()
        var count = 0
        for (i in 0 until deck.size - 2) {
            for (j in i + 1 until deck.size - 1) {
                for (k in j + 1 until deck.size) {
                    if (isSet(deck[i], deck[j], deck[k])) count++
                }
            }
        }
        assertEquals(1080, count)
    }

    @Test
    fun findSetIndicesMatchesBruteForceOnRandomTables() {
        val random = Random(7)
        repeat(200) {
            val table = fullDeck().shuffled(random).take(12)
            val found = findSetIndices(table)
            val bruteForce = bruteForceHasSet(table)
            assertEquals(bruteForce, found != null)
            if (found != null) {
                assertEquals(3, found.toSet().size)
                val (i, j, k) = found
                assertTrue(isSet(table[i], table[j], table[k]))
            }
        }
    }

    @Test
    fun findSetIndicesIsNullForASetFreeTable() {
        assertNull(findSetIndices(capSetCards(12)))
        assertNull(findSet(capSetCards(15)))
    }

    @Test
    fun findSetIsNullForEmptyTable() {
        assertNull(findSetIndices(emptyList()))
        assertNull(findSet(emptyList()))
    }

    @Test
    fun drawOfThreeIsNotEnoughToGuaranteeASetBut21Is() {
        // A 12-card cap set can exist, so findSet must tolerate it.
        assertNull(findSetIndices(capSetCards(12)))
        // 21 cards always contain a set (max cap set in AG(4,3) is 20).
        val twentyOne = fullDeck().shuffled(Random(11)).take(21)
        assertNotNull(findSetIndices(twentyOne))
    }

    private fun bruteForceHasSet(cards: List<Card>): Boolean {
        for (i in 0 until cards.size - 2) {
            for (j in i + 1 until cards.size - 1) {
                for (k in j + 1 until cards.size) {
                    if (isSet(cards[i], cards[j], cards[k])) return true
                }
            }
        }
        return false
    }
}

/**
 * Cards restricted to the binary subspace {0,1}^4 of F_3^4. No three of these
 * can form a set, because a set needs a third value in every attribute.
 */
internal fun capSetCards(count: Int = 12): List<Card> = buildList {
    for (bits in 0 until 16) {
        add(
            Card(
                number = if (bits and 1 == 0) CardNumber.ONE else CardNumber.TWO,
                color = if (bits and 2 == 0) CardColor.RED else CardColor.GREEN,
                shape = if (bits and 4 == 0) CardShape.DIAMOND else CardShape.SQUIGGLE,
                shading = if (bits and 8 == 0) CardShading.SOLID else CardShading.STRIPED,
            ),
        )
    }
}.take(count)

/** Three cards that share color/shape/shading and span all three numbers. */
internal fun ovalTrio(): List<Card> = listOf(
    Card(CardNumber.ONE, CardColor.PURPLE, CardShape.OVAL, CardShading.OPEN),
    Card(CardNumber.TWO, CardColor.PURPLE, CardShape.OVAL, CardShading.OPEN),
    Card(CardNumber.THREE, CardColor.PURPLE, CardShape.OVAL, CardShading.OPEN),
)
