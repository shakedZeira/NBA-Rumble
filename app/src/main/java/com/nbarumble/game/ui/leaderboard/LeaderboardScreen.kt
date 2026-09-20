package com.nbarumble.game.ui.leaderboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbarumble.game.data.model.LeaderboardEntry
import com.nbarumble.game.ui.theme.CourtNight
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.SurfaceRaised

@Composable
fun LeaderboardRoute(
    vm: LeaderboardViewModel,
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CourtNight)
    ) {
        TopBar(onBack = onBack)

        when {
            state.isLoading && state.entries.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }

            state.error != null && state.entries.isEmpty() -> {
                val error = state.error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffWhite.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = vm::retry,
                        modifier = Modifier.padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        Text("RETRY", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.entries.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No games played yet — go win one!",
                        style = MaterialTheme.typography.titleMedium,
                        color = OffWhite.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.entries) { index, entry ->
                        LeaderboardRow(
                            rank = index + 1,
                            entry = entry,
                            highlighted = entry.uid == state.myUid
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OffWhite)
        }
        Text(
            text = "Leaderboard",
            style = MaterialTheme.typography.headlineSmall,
            color = OffWhite,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, highlighted: Boolean) {
    val accent = if (highlighted) Orange else SurfaceRaised
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) Orange.copy(alpha = 0.15f) else SurfacePanel,
        border = BorderStroke(width = 2.dp, color = accent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (highlighted) Orange else SurfaceRaised
            ) {
                Text(
                    text = "$rank",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (highlighted) Color.Black else IceBlue,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = entry.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) Orange else OffWhite,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold
            )
            Text(
                text = "${entry.wins} win${if (entry.wins == 1L) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) Orange else IceBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}