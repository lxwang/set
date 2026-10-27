package com.example.set

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.set.data.GameStore
import com.example.set.data.SettingsRepository
import com.example.set.engine.GameState
import com.example.set.game.GameResult
import com.example.set.game.GameScreen
import com.example.set.start.StartScreen
import com.example.set.summary.SummaryScreen

private enum class Screen { START, GAME, SUMMARY }

/**
 * Three surfaces, no navigation library. A saved game opens directly on start;
 * everything else is a sealed route in a `when`.
 */
@Composable
fun SetApp(settings: SettingsRepository, store: GameStore) {
    var resumeState by remember { mutableStateOf(store.load()) }
    var initialState by remember { mutableStateOf<GameState?>(resumeState) }
    var screen by rememberSaveable {
        mutableStateOf(if (resumeState != null) Screen.GAME else Screen.START)
    }
    var activeMode by rememberSaveable { mutableStateOf(resumeState?.mode ?: settings.mode) }
    var gameKey by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GameResult?>(null) }

    when (screen) {
        Screen.START -> StartScreen(
            settings = settings,
            hasSavedGame = resumeState != null,
            onPlay = { mode ->
                store.clear()
                resumeState = null
                initialState = null
                activeMode = mode
                gameKey++
                result = null
                screen = Screen.GAME
            },
            onResume = {
                resumeState?.let { saved ->
                    initialState = saved
                    activeMode = saved.mode
                    gameKey++
                    screen = Screen.GAME
                }
            },
        )

        Screen.GAME -> GameScreen(
            mode = activeMode,
            settings = settings,
            store = store,
            gameKey = gameKey,
            initialState = initialState,
            onExit = {
                resumeState = store.load()
                initialState = null
                screen = Screen.START
            },
            onGameOver = { finished ->
                resumeState = null
                initialState = null
                result = finished
                screen = Screen.SUMMARY
            },
        )

        Screen.SUMMARY -> {
            val finished = result
            if (finished == null) {
                LaunchedEffect(Unit) { screen = Screen.START }
            } else {
                SummaryScreen(
                    result = finished,
                    onPlayAgain = {
                        gameKey++
                        initialState = null
                        screen = Screen.GAME
                    },
                    onHome = {
                        resumeState = null
                        screen = Screen.START
                    },
                )
            }
        }
    }
}
