package com.nbarumble.game.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.ui.theme.Danger
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.Success

@Composable
fun HomeRoute(vm: HomeViewModel, onNav: (String) -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let {
            onNav(it)
            vm.consumeNavigation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏀 NBA RUMBLE",
            style = MaterialTheme.typography.displaySmall,
            color = Orange,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = "1v1 chess-clock trivia. Identify the player before your clock empties.",
            style = MaterialTheme.typography.bodyMedium,
            color = OffWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        ConnectionBadge(state = state)

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "DIFFICULTY",
            style = MaterialTheme.typography.labelMedium,
            color = OffWhite.copy(alpha = 0.7f),
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Difficulty.entries.forEach { d ->
                val selected = state.difficulty == d
                Surface(
                    onClick = { vm.setDifficulty(d) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (selected) Orange else SurfacePanel,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = d.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.Black else OffWhite
                        )
                        Text(
                            text = when (d) {
                                Difficulty.EASY -> "Icons only"
                                Difficulty.MEDIUM -> "Stars & vets"
                                Difficulty.HARD -> "Full roster"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.Black.copy(alpha = 0.7f) else OffWhite.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Time bank (s)",
                value = state.bankSec,
                onChange = vm::setBank,
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Skip penalty (±s)",
                value = state.penaltySec,
                onChange = vm::setPenalty,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = vm::createRoom,
            enabled = state.conn == HomeViewModel.ConnState.CONNECTED && !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange)
        ) {
            Text(
                if (state.busy) "Working…" else "CREATE ROOM",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = SurfacePanel)
            Text(
                "  or  ",
                color = OffWhite.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = SurfacePanel)
        }

        OutlinedTextField(
            value = state.joinCode,
            onValueChange = vm::setJoinCode,
            label = { Text("Room code") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
        )

        OutlinedButton(
            onClick = vm::joinRoom,
            enabled = state.conn == HomeViewModel.ConnState.CONNECTED && !state.busy && state.joinCode.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(50.dp)
        ) {
            Text("JOIN", fontWeight = FontWeight.Bold, color = IceBlue)
        }

        state.message?.let {
            Text(
                text = it,
                color = Danger,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun ConnectionBadge(state: HomeViewModel.UiState) {
    val connecting = state.conn == HomeViewModel.ConnState.CONNECTING
    val color = when (state.conn) {
        HomeViewModel.ConnState.CONNECTED -> Success
        HomeViewModel.ConnState.ERROR -> Danger
        HomeViewModel.ConnState.CONNECTING -> OffWhite
    }
    Surface(
        modifier = Modifier.padding(top = 14.dp),
        shape = CircleShape,
        color = SurfacePanel
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            }
            Text(
                text = when (state.conn) {
                    HomeViewModel.ConnState.CONNECTED -> "  Firebase connected"
                    HomeViewModel.ConnState.ERROR -> "  Firebase not configured"
                    HomeViewModel.ConnState.CONNECTING -> "  Connecting…"
                },
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    )
}