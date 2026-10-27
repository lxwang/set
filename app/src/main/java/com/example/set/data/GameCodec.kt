package com.example.set.data

import com.example.set.engine.Card
import com.example.set.engine.CardColor
import com.example.set.engine.CardNumber
import com.example.set.engine.CardShading
import com.example.set.engine.CardShape
import com.example.set.engine.GameMode
import com.example.set.engine.GamePhase
import com.example.set.engine.GameState

/**
 * Text codec for an in-progress game. Pure Kotlin so the round trip is unit
 * testable. A card is four attribute ordinals; "-" is a hole in the table.
 *
 * Example: `v1;mode=TIMED;sets=3;...;table=0000,-,1111;deck=2222,0120`
 */
object GameCodec {
    private const val VERSION = "v1"

    fun encode(state: GameState): String = buildString {
        append(VERSION)
        append(";mode=").append(state.mode.name)
        append(";sets=").append(state.setsFound)
        append(";mistakes=").append(state.mistakes)
        append(";hints=").append(state.hintsUsed)
        append(";elapsed=").append(state.elapsedSeconds)
        append(";started=").append(if (state.started) 1 else 0)
        append(";extra=").append(state.extraDeals)
        append(";deals=").append(state.deals)
        append(";table=").append(state.table.joinToString(",") { it?.let(::encodeCard) ?: "-" })
        append(";deck=").append(state.deck.joinToString(",") { encodeCard(it) })
    }

    fun decode(text: String): GameState? {
        val parts = text.split(";")
        if (parts.firstOrNull() != VERSION) return null

        val fields = parts.drop(1).mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.take(separator) to part.substring(separator + 1)
        }.toMap()

        return runCatching {
            val state = GameState(
                table = fields.getValue("table").split(",").map { token ->
                    if (token == "-") null else decodeCard(token)
                },
                deck = fields.getValue("deck")
                    .takeIf { it.isNotEmpty() }
                    ?.split(",")
                    ?.map(::decodeCard)
                    ?: emptyList(),
                setsFound = fields.getValue("sets").toInt(),
                mistakes = fields.getValue("mistakes").toInt(),
                hintsUsed = fields.getValue("hints").toInt(),
                phase = GamePhase.PLAYING, // a saved game always resumes playable
                mode = GameMode.valueOf(fields.getValue("mode")),
                elapsedSeconds = fields.getValue("elapsed").toLong(),
                started = fields.getValue("started") == "1",
                extraDeals = fields.getValue("extra").toInt(),
                deals = fields.getValue("deals").toInt(),
            )
            if (isConsistent(state)) state else null
        }.getOrNull()
    }

    private fun isConsistent(state: GameState): Boolean {
        if (state.setsFound !in 0..27) return false
        if (state.table.size !in GameState.TABLE_SIZE..18) return false
        if (state.table.size > GameState.TABLE_SIZE && state.table.size % 3 != 0) return false
        if (state.cardsOnTable + state.deck.size + 3 * state.setsFound != 81) return false
        val cards = state.table.filterNotNull() + state.deck
        return cards.size == cards.distinct().size
    }

    private fun encodeCard(card: Card): String = buildString {
        append(card.number.ordinal)
        append(card.color.ordinal)
        append(card.shape.ordinal)
        append(card.shading.ordinal)
    }

    private fun decodeCard(token: String): Card {
        require(token.length == 4 && token.all { it.isDigit() })
        val digits = token.map { it.digitToInt() }
        return Card(
            number = CardNumber.entries[digits[0]],
            color = CardColor.entries[digits[1]],
            shape = CardShape.entries[digits[2]],
            shading = CardShading.entries[digits[3]],
        )
    }
}
