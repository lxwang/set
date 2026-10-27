package com.example.set.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.set.R
import com.example.set.data.GameStore
import com.example.set.data.SettingsRepository
import com.example.set.engine.GameMode
import com.example.set.engine.GamePhase
import com.example.set.engine.GameState
import com.example.set.engine.formatDuration
import com.example.set.engine.totalSeconds
import com.example.set.ui.CardFace
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    mode: GameMode,
    settings: SettingsRepository,
    store: GameStore,
    gameKey: Int,
    initialState: GameState?,
    onExit: () -> Unit,
    onGameOver: (GameResult) -> Unit,
) {
    val viewModel: GameViewModel = viewModel(key = "game-$gameKey") {
        GameViewModel(mode, settings, store, initialState)
    }
    val state by viewModel.state.collectAsState()

    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    val shake = remember { Animatable(0f) }

    // Leaving the game by any route pauses the clock and saves the game.
    val exit: () -> Unit = {
        viewModel.leave()
        onExit()
    }
    BackHandler(onBack = exit)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.leave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.GAME_OVER) {
            viewModel.result?.let(onGameOver)
        }
    }
    LaunchedEffect(state.extraDeals) {
        if (state.extraDeals > 0) {
            bannerVisible = true
            delay(2_000)
            bannerVisible = false
        }
    }
    LaunchedEffect(state.mistakes) {
        if (state.mistakes > 0) {
            shakeTrigger++
            if (settings.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    LaunchedEffect(state.setsFound) {
        if (state.setsFound > 0 && settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            repeat(3) {
                shake.animateTo(6f, tween(50))
                shake.animateTo(-6f, tween(50))
            }
            shake.animateTo(0f, tween(50))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            GameHeader(
                state = state,
                mode = mode,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onExit = exit,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                hapticsEnabled = settings.hapticsEnabled,
                colorblindEnabled = settings.colorblindPalette,
                onToggleHaptics = { settings.updateHaptics(!settings.hapticsEnabled) },
                onToggleColorblind = { settings.updateColorblindPalette(!settings.colorblindPalette) },
                onHowToPlay = { showHowTo = true },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .offset { IntOffset(shake.value.roundToInt(), 0) },
            ) {
                Board(
                    state = state,
                    colorblindPalette = settings.colorblindPalette,
                    onTap = viewModel::tap,
                )

                if (bannerVisible) {
                    Text(
                        text = stringResource(R.string.no_set_banner),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            GameFooter(
                state = state,
                onHint = viewModel::hint,
                onNewGame = {
                    if (state.started) confirmNewGame = true else viewModel.newGame()
                },
            )
        }

        if (state.phase == GamePhase.PAUSED) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.pause_title)) },
                text = { Text(stringResource(R.string.pause_body)) },
                confirmButton = {
                    Button(onClick = viewModel::resume) {
                        Text(stringResource(R.string.action_resume))
                    }
                },
                dismissButton = {
                    TextButton(onClick = exit) { Text(stringResource(R.string.action_home)) }
                },
            )
        }

        if (confirmNewGame) {
            AlertDialog(
                onDismissRequest = { confirmNewGame = false },
                title = { Text(stringResource(R.string.new_game_title)) },
                text = { Text(stringResource(R.string.new_game_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmNewGame = false
                            viewModel.newGame()
                        },
                    ) { Text(stringResource(R.string.new_game_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmNewGame = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showHowTo) {
            AlertDialog(
                onDismissRequest = { showHowTo = false },
                title = { Text(stringResource(R.string.how_to_play)) },
                text = { Text(stringResource(R.string.how_to_play_body)) },
                confirmButton = {
                    TextButton(onClick = { showHowTo = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
            )
        }
    }
}

@Composable
private fun GameHeader(
    state: GameState,
    mode: GameMode,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    hapticsEnabled: Boolean,
    colorblindEnabled: Boolean,
    onToggleHaptics: () -> Unit,
    onToggleColorblind: () -> Unit,
    onHowToPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.header_sets, state.setsFound),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (mode == GameMode.TIMED) {
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = formatDuration(state.totalSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                text = stringResource(R.string.header_in_deck, state.deck.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (state.phase == GamePhase.PAUSED) R.string.action_resume
                                else R.string.action_pause,
                            ),
                        )
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        if (state.phase == GamePhase.PAUSED) onResume() else onPause()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_haptics)) },
                    trailingIcon = {
                        if (hapticsEnabled) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = { onToggleHaptics() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_colorblind)) },
                    onClick = { onToggleColorblind() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.how_to_play)) },
                    onClick = {
                        onMenuOpenChange(false)
                        onHowToPlay()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_home)) },
                    onClick = {
                        onMenuOpenChange(false)
                        onExit()
                    },
                )
            }
        }
    }
}

@Composable
private fun GameFooter(
    state: GameState,
    onHint: () -> Unit,
    onNewGame: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onHint,
            enabled = state.hintsRemaining > 0 && state.phase == GamePhase.PLAYING,
        ) {
            Text(
                if (state.hintsRemaining > 0) {
                    stringResource(R.string.action_hint_left, state.hintsRemaining)
                } else {
                    stringResource(R.string.hint_none)
                },
            )
        }
        TextButton(onClick = onNewGame) {
            Text(stringResource(R.string.action_new_game))
        }
    }
}

@Composable
private fun Board(
    state: GameState,
    colorblindPalette: Boolean,
    onTap: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        val columns = 3
        val rows = (state.table.size + columns - 1) / columns
        val gap = 12.dp
        val widthLimit = (maxWidth - gap * (columns - 1)) / columns
        val heightLimit = (maxHeight - gap * (rows - 1)) / rows / 1.4f
        val cardWidth = minOf(widthLimit, heightLimit).coerceAtLeast(64.dp)
        val cardHeight = cardWidth * 1.4f

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until columns) {
                        val index = row * columns + column
                        if (index < state.table.size && state.table[index] != null) {
                            CardFace(
                                card = state.table[index]!!,
                                colorblindPalette = colorblindPalette,
                                isSelected = index in state.selected,
                                isHinted = state.hintIndices?.contains(index) == true,
                                isGold = index in state.goldSlots,
                                animateDeal = index in state.dealtSlots,
                                dealKey = state.deals,
                                onClick = { onTap(index) },
                                modifier = Modifier.size(cardWidth, cardHeight),
                            )
                        } else {
                            Spacer(Modifier.size(cardWidth, cardHeight))
                        }
                    }
                }
            }
        }
    }
}
