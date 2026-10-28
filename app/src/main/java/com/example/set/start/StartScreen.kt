package com.example.set.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.set.R
import com.example.set.data.SettingsRepository
import com.example.set.engine.CardColor
import com.example.set.engine.GameMode
import com.example.set.engine.formatDuration
import com.example.set.ui.theme.cardColor

@Composable
fun StartScreen(
    settings: SettingsRepository,
    hasSavedGame: Boolean,
    onPlay: (GameMode) -> Unit,
    onResume: () -> Unit,
) {
    val best = settings.best(settings.mode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // SET in the three card colours, using the active palette.
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val titleDescription = stringResource(R.string.start_title)
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = titleDescription
            },
        ) {
            listOf(
                CardColor.RED to "S",
                CardColor.GREEN to "E",
                CardColor.PURPLE to "T",
            ).forEach { (color, letter) ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = cardColor(color, settings.colorblindPalette, darkTheme),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.start_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        ModeOption(
            selected = settings.mode == GameMode.TIMED,
            title = stringResource(R.string.mode_timed),
            detail = stringResource(R.string.mode_timed_detail),
            onClick = { settings.updateMode(GameMode.TIMED) },
        )
        Spacer(Modifier.height(12.dp))
        ModeOption(
            selected = settings.mode == GameMode.RELAXED,
            title = stringResource(R.string.mode_relaxed),
            detail = stringResource(R.string.mode_relaxed_detail),
            onClick = { settings.updateMode(GameMode.RELAXED) },
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = when {
                best == null -> stringResource(R.string.start_best_none)
                best.perfect -> stringResource(
                    R.string.start_best_perfect,
                    formatDuration(best.seconds),
                )
                else -> stringResource(
                    R.string.start_best,
                    best.sets,
                    formatDuration(best.seconds),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { if (hasSavedGame) onResume() else onPlay(settings.mode) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = stringResource(
                    if (hasSavedGame) R.string.action_resume_game else R.string.start_play,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (hasSavedGame) {
            TextButton(onClick = { onPlay(settings.mode) }) {
                Text(stringResource(R.string.action_new_game_start))
            }
        }
    }
}

@Composable
private fun ModeOption(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
