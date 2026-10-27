package com.example.set.summary

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.set.R
import com.example.set.engine.MISTAKE_PENALTY_SECONDS
import com.example.set.engine.formatDuration
import com.example.set.game.GameResult

@Composable
fun SummaryScreen(
    result: GameResult,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (result.perfect) R.string.summary_perfect else R.string.summary_cleared,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = result.setsFound.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(0.dp))
            Text(
                text = " " + stringResource(R.string.summary_sets),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (result.isNewBest) {
            Text(
                text = stringResource(R.string.summary_best_badge),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (result.mode == com.example.set.engine.GameMode.TIMED) {
                StatRow(
                    label = stringResource(R.string.summary_time),
                    value = formatDuration(result.seconds),
                )
            }
            StatRow(
                label = stringResource(R.string.summary_mistakes),
                value = result.mistakes.toString(),
            )
            StatRow(
                label = stringResource(R.string.summary_hints),
                value = result.hintsUsed.toString(),
            )
            if (result.mode == com.example.set.engine.GameMode.TIMED && result.mistakes > 0) {
                StatRow(
                    label = stringResource(R.string.summary_penalty),
                    value = "+${result.mistakes * MISTAKE_PENALTY_SECONDS.toInt()}s",
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!result.perfect) {
            Text(
                text = stringResource(
                    R.string.summary_leftover,
                    81 - 3 * result.setsFound,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (result.mode == com.example.set.engine.GameMode.RELAXED) {
            Text(
                text = stringResource(R.string.summary_relaxed_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.action_play_again),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TextButton(onClick = onHome) {
            Text(stringResource(R.string.action_home))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
