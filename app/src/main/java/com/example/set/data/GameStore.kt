package com.example.set.data

import android.content.Context
import com.example.set.engine.GameState

/** Persists the single in-progress game so the app can resume where it left off. */
class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("set.game", Context.MODE_PRIVATE)

    fun save(state: GameState) {
        prefs.edit().putString(KEY_GAME, GameCodec.encode(state)).apply()
    }

    fun load(): GameState? = prefs.getString(KEY_GAME, null)?.let(GameCodec::decode)

    fun clear() {
        prefs.edit().remove(KEY_GAME).apply()
    }

    private companion object {
        const val KEY_GAME = "in_progress"
    }
}
