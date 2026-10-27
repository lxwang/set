package com.example.set.engine

/**
 * Three cards are a set iff, for every attribute independently, the values are
 * all the same or all different. Two of one and one of another never counts.
 */
fun isSet(a: Card, b: Card, c: Card): Boolean =
    allSameOrAllDifferent(a.number, b.number, c.number) &&
        allSameOrAllDifferent(a.color, b.color, c.color) &&
        allSameOrAllDifferent(a.shape, b.shape, c.shape) &&
        allSameOrAllDifferent(a.shading, b.shading, c.shading)

private fun <T> allSameOrAllDifferent(a: T, b: T, c: T): Boolean =
    (a == b && b == c) || (a != b && b != c && a != c)

/** Indices of one valid set, or null when the table is set-free. */
fun findSetIndices(cards: List<Card>): List<Int>? {
    for (i in 0 until cards.size - 2) {
        for (j in i + 1 until cards.size - 1) {
            for (k in j + 1 until cards.size) {
                if (isSet(cards[i], cards[j], cards[k])) return listOf(i, j, k)
            }
        }
    }
    return null
}

/** One valid set, or null. Convenience for tests and hints. */
fun findSet(cards: List<Card>): Triple<Card, Card, Card>? {
    val indices = findSetIndices(cards) ?: return null
    return Triple(cards[indices[0]], cards[indices[1]], cards[indices[2]])
}
