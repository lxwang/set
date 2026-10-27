package com.example.set.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.set.engine.GameMode

data class BestResult(
    val sets: Int,
    val seconds: Long,
    val perfect: Boolean,
)

/**
 * Small, synchronous settings store. Five primitives and a best-result triple do
 * not justify an async dependency; SharedPreferences reads are instant at startup
 * and writes are applied off the main thread.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("set.settings", Context.MODE_PRIVATE)

    var mode by mutableStateOf(loadMode())
        private set

    var hapticsEnabled by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set

    var colorblindPalette by mutableStateOf(prefs.getBoolean(KEY_COLORBLIND, false))
        private set

    fun updateMode(newMode: GameMode) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
    }

    fun updateHaptics(enabled: Boolean) {
        hapticsEnabled = enabled
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    fun updateColorblindPalette(enabled: Boolean) {
        colorblindPalette = enabled
        prefs.edit().putBoolean(KEY_COLORBLIND, enabled).apply()
    }

    fun best(mode: GameMode): BestResult? {
        val sets = prefs.getInt(key(mode, "sets"), 0)
        if (sets == 0) return null
        return BestResult(
            sets = sets,
            seconds = prefs.getLong(key(mode, "seconds"), 0L),
            perfect = prefs.getBoolean(key(mode, "perfect"), false),
        )
    }

    /** Stores the result if it beats the current best. Returns true when it did. */
    fun record(mode: GameMode, result: BestResult): Boolean {
        val current = best(mode)
        val isBetter = current == null || compare(result, current) > 0
        if (isBetter) {
            prefs.edit()
                .putInt(key(mode, "sets"), result.sets)
                .putLong(key(mode, "seconds"), result.seconds)
                .putBoolean(key(mode, "perfect"), result.perfect)
                .apply()
        }
        return isBetter
    }

    private fun compare(a: BestResult, b: BestResult): Int = when {
        a.sets != b.sets -> a.sets.compareTo(b.sets)
        a.seconds != b.seconds -> b.seconds.compareTo(a.seconds) // lower time is better
        a.perfect != b.perfect -> if (a.perfect) 1 else -1
        else -> 0
    }

    private fun loadMode(): GameMode {
        val name = prefs.getString(KEY_MODE, null)
        return GameMode.entries.firstOrNull { it.name == name } ?: GameMode.TIMED
    }

    private fun key(mode: GameMode, field: String): String = "best_${mode.name.lowercase()}_$field"

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_HAPTICS = "haptics"
        const val KEY_COLORBLIND = "colorblind"
    }
}
