package com.example.set.engine

enum class CardNumber(val value: Int, val label: String) {
    ONE(1, "one"),
    TWO(2, "two"),
    THREE(3, "three"),
}

enum class CardColor(val label: String) {
    RED("red"),
    GREEN("green"),
    PURPLE("purple"),
}

enum class CardShape(val label: String) {
    DIAMOND("diamond"),
    SQUIGGLE("squiggle"),
    OVAL("oval"),
}

enum class CardShading(val label: String) {
    SOLID("solid"),
    STRIPED("striped"),
    OPEN("open"),
}

/**
 * A Set card: the tuple of four independent attributes. Every one of the
 * 3^4 = 81 combinations exists exactly once in a deck.
 */
data class Card(
    val number: CardNumber,
    val color: CardColor,
    val shape: CardShape,
    val shading: CardShading,
) {
    /** Spoken form, e.g. "two solid red diamonds". */
    val description: String
        get() {
            val shapeWord = if (number == CardNumber.ONE) shape.label else "${shape.label}s"
            return "${number.label} ${shading.label} ${color.label} $shapeWord"
        }
}
