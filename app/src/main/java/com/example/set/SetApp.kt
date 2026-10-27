package com.example.set

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.set.data.SettingsRepository
import com.example.set.engine.GameMode
import com.example.set.game.GameResult
import com.example.set.game.GameScreen
import com.example.set.start.StartScreen
import com.example.set.summary.SummaryScreen

private enum class Screen { START, GAME, SUMMARY }

/**
 * Three surfaces, no navigation library: a sealed route in a `when` is less code
 * than a NavHost and has no back-stack surprises.
 */
@Composable
fun SetApp(settings: SettingsRepository) {
    var screen by rememberSaveable { mutableStateOf(Screen.START) }
    var activeMode by rememberSaveable { mutableStateOf(settings.mode) }
    var gameKey by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GameResult?>(null) }

    when (screen) {
        Screen.START -> StartScreen(settings) { mode ->
            activeMode = mode
            gameKey++
            result = null
            screen = Screen.GAME
        }

        Screen.GAME -> GameScreen(
            mode = activeMode,
            settings = settings,
            gameKey = gameKey,
            onExit = { screen = Screen.START },
            onGameOver = { finished ->
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
                        screen = Screen.GAME
                    },
                    onHome = { screen = Screen.START },
                )
            }
        }
    }
}
