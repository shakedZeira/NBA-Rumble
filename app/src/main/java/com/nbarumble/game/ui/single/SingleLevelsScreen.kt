package com.nbarumble.game.ui.single

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.SurfaceRaised
import java.util.Locale

fun formatScore(value: Long): String = String.format(Locale.US, "%,d", value)

@Composable
fun SingleRoute(
    vm: SingleViewModel,
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    if (state.selectedLevel == null) {
        SingleLevelsScreen(
            state = state,
            onLevelSelected = vm::onLevelSelected,
            onBack = onBack
        )
    } else {
        SingleGameScreen(vm = vm, state = state)
    }
}

@Composable
private fun SingleLevelsScreen(
    state: SingleViewModel.UiState,
    onLevelSelected: (SingleLevel) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home", tint = OffWhite)
            }
            Column {
                Text(
                    text = "Single Player",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OffWhite,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Pick a level",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceBlue
                )
            }
        }

        SingleLevel.entries.forEach { level ->
            LevelCard(
                level = level,
                best = state.bestByLevel[level] ?: 0L,
                onClick = { onLevelSelected(level) }
            )
        }
    }
}

@Composable
private fun LevelCard(level: SingleLevel, best: Long, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfacePanel,
        border = BorderStroke(1.dp, SurfaceRaised)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = Orange,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = level.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OffWhite
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "BEST",
                    style = MaterialTheme.typography.labelSmall,
                    color = IceBlue
                )
                Text(
                    text = formatScore(best),
                    style = MaterialTheme.typography.titleMedium,
                    color = OffWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}