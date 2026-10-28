package com.example.set.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.set.data.BestResult
import com.example.set.data.GameStore
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
    private val store: GameStore,
    initial: GameState? = null,
) : ViewModel() {

    private val opening = initial ?: GameState.newGame(mode)

    private val _state = MutableStateFlow(
        opening.copy(
            phase = GamePhase.PLAYING,
            selected = emptyList(),
            hintIndices = null,
            goldSlots = emptyList(),
            dealtSlots = emptyList(),
        ),
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** Set once, when the game ends; the screen navigates on it. */
    var result: GameResult? = null
        private set

    private var hintJob: Job? = null
    private var blinkJob: Job? = null
    private var recorded = false

    init {
        if (initial != null) {
            // Resuming: keep the saved clock, but make sure it is persisted even
            // if the process dies before the next action.
            store.save(_state.value)
        }
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

    /**
     * A valid trio is not removed immediately: it stays on the table with
     * [GameState.goldSlots] set so the UI can blink its outline, then resolves.
     */
    fun tap(index: Int) {
        val current = _state.value
        if (current.phase != GamePhase.PLAYING || current.isResolving) return

        val candidate = current.tap(index)
        if (candidate.setsFound > current.setsFound) {
            val trio = current.selected + index
            _state.value = current.copy(selected = trio, goldSlots = trio, started = true)
            blinkJob?.cancel()
            blinkJob = viewModelScope.launch {
                delay(GOLD_BLINK_MILLIS)
                val resolved = current.tap(index).ensurePlayable()
                update(resolved)
                // Replacement cards slide in now; retire the flag so the lifted
                // z-order and the animation state do not outlive the slide.
                clearDealtSlotsLater(resolved)
            }
            return
        }

        val resolved = candidate.ensurePlayable()
        update(resolved)
        clearDealtSlotsLater(resolved)
        if (resolved.invalidSlots.isNotEmpty()) {
            viewModelScope.launch {
                delay(INVALID_SHAKE_MILLIS)
                if (_state.value.invalidSlots.isNotEmpty()) {
                    _state.value = _state.value.copy(invalidSlots = emptyList())
                }
            }
        }
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

    /** Called when the game screen is being left. Freezes the clock and saves. */
    fun leave() {
        update(_state.value.pause())
    }

    fun newGame() {
        hintJob?.cancel()
        blinkJob?.cancel()
        recorded = false
        result = null
        update(GameState.newGame(mode))
    }

    /** dealtSlots drives the slide-in animation and the z-order; retire it after. */
    private fun clearDealtSlotsLater(state: GameState) {
        if (state.dealtSlots.isEmpty()) return
        viewModelScope.launch {
            delay(DEAL_ANIMATION_MILLIS)
            if (_state.value.dealtSlots.isNotEmpty()) {
                _state.value = _state.value.copy(dealtSlots = emptyList())
            }
        }
    }

    private fun update(next: GameState) {
        _state.value = next
        if (next.phase == GamePhase.GAME_OVER) {
            store.clear()
            if (!recorded) {
                recorded = true
                val seconds = next.totalSeconds
                val perfect = next.cardsOnTable == 0
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
        } else {
            store.save(next)
        }
    }

    private companion object {
        /** Gold outline, then a 6% pop, then removal. */
        const val GOLD_BLINK_MILLIS = 520L

        /** Per-card invalid shake: 3 cycles of 50 ms each way, plus slack. */
        const val INVALID_SHAKE_MILLIS = 380L

        /** Slide-in lasts 360 ms; keep the lifted z-order until it is done. */
        const val DEAL_ANIMATION_MILLIS = 700L
    }
}
