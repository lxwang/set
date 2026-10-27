package com.example.set.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.set.data.BestResult
import com.example.set.data.SettingsRepository
import com.example.set.engine.GameMode
import com.example.set.engine.GamePhase
import com.example.set.engine.GameState
import com.example.set.engine.totalSeconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GameResult(
    val mode: GameMode,
    val setsFound: Int,
    val seconds: Long,
    val mistakes: Int,
    val hintsUsed: Int,
    val perfect: Boolean,
    val isNewBest: Boolean,
)

class GameViewModel(
    private val mode: GameMode,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GameState.newGame(mode))
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** Set once, when the game ends; the screen navigates on it. */
    var result: GameResult? = null
        private set

    private var hintJob: Job? = null
    private var recorded = false

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _state.value
                if (current.started && current.phase == GamePhase.PLAYING) {
                    _state.value = current.withElapsed(current.elapsedSeconds + 1)
                }
            }
        }
    }

    fun tap(index: Int) {
        update(_state.value.tap(index).ensurePlayable())
    }

    fun hint() {
        val hinted = _state.value.hint()
        update(hinted)
        if (hinted.hintIndices != null) {
            hintJob?.cancel()
            hintJob = viewModelScope.launch {
                delay(2_500)
                _state.value = _state.value.clearHint()
            }
        }
    }

    fun pause() = update(_state.value.pause())

    fun resume() = update(_state.value.resume())

    fun newGame() {
        hintJob?.cancel()
        recorded = false
        result = null
        update(GameState.newGame(mode))
    }

    fun hasHintsLeft(): Boolean = _state.value.hintsRemaining > 0

    private fun update(next: GameState) {
        _state.value = next
        if (next.phase == GamePhase.GAME_OVER && !recorded) {
            recorded = true
            val seconds = next.totalSeconds
            val perfect = next.table.isEmpty()
            val isNewBest = settings.record(
                mode,
                BestResult(sets = next.setsFound, seconds = seconds, perfect = perfect),
            )
            result = GameResult(
                mode = mode,
                setsFound = next.setsFound,
                seconds = seconds,
                mistakes = next.mistakes,
                hintsUsed = next.hintsUsed,
                perfect = perfect,
                isNewBest = isNewBest,
            )
        }
    }
}
