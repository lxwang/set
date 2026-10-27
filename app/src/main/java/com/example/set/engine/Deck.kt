package com.example.set.engine

import kotlin.random.Random

/** The 81 unique cards, in a deterministic order. */
fun fullDeck(): List<Card> = buildList {
    for (number in CardNumber.entries) {
        for (color in CardColor.entries) {
            for (shape in CardShape.entries) {
                for (shading in CardShading.entries) {
                    add(Card(number, color, shape, shading))
                }
            }
        }
    }
}

fun shuffledDeck(random: Random = Random.Default): List<Card> = fullDeck().shuffled(random)
