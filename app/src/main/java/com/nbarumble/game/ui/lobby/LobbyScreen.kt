package com.nbarumble.game.ui.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbarumble.game.data.model.Room
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.SurfaceRaised

@Composable
fun LobbyRoute(
    vm: LobbyViewModel,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(state.room?.isPlaying) {
        if (state.room?.isPlaying == true) onStart()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (state.isHost) "ROOM CREATED" else "JOIN ROOM",
            style = MaterialTheme.typography.headlineSmall,
            color = OffWhite,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Send this code to your opponent",
            style = MaterialTheme.typography.bodyMedium,
            color = OffWhite.copy(alpha = 0.7f)
        )

        Surface(
            modifier = Modifier.padding(top = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = SurfaceRaised
        ) {
            Text(
                text = state.code,
                style = MaterialTheme.typography.displayMedium,
                color = Orange,
                fontWeight = FontWeight.Black,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }

        val clipboard = LocalClipboardManager.current
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(state.code)) },
            modifier = Modifier.padding(top = 14.dp)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = IceBlue)
            Text("  Copy code", color = IceBlue, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 24.dp),
            color = SurfacePanel
        )

        when {
            state.missing -> {
                Text(
                    text = "This room no longer exists.",
                    color = Orange,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("GO BACK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            state.room?.isPlaying == true -> {
                Text(
                    text = "Opponent found — starting!",
                    style = MaterialTheme.typography.titleMedium,
                    color = IceBlue
                )
                CircularProgressIndicator(modifier = Modifier.padding(top = 14.dp).size(26.dp))
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Text(
                        text = "  Waiting for opponent",
                        style = MaterialTheme.typography.titleMedium,
                        color = OffWhite
                    )
                }
                PlayerList(room = state.room)

                if (state.isHost) {
                    Text(
                        text = "Opponent joins with this code and you're off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OffWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 22.dp)
                    )
                } else {
                    Text(
                        text = "Joining this room — you'll start as soon as both players are in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OffWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerList(room: Room?) {
    val joined = room?.playerCount ?: 0
    Column(
        modifier = Modifier.padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Orange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "  Host in room",
                style = MaterialTheme.typography.labelLarge,
                color = OffWhite
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (joined >= 1) "1/2" else "0/2",
                style = MaterialTheme.typography.labelLarge,
                color = IceBlue
            )
        }
        if (joined >= 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = IceBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "  Opponent joined",
                    style = MaterialTheme.typography.labelLarge,
                    color = IceBlue
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}