package com.nbarumble.game.ui.game

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.nbarumble.game.game.ClockEngine
import com.nbarumble.game.speech.SpeechManager
import com.nbarumble.game.ui.theme.Danger
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.SurfaceRaised
import com.nbarumble.game.ui.theme.Success
import kotlinx.coroutines.delay

@Composable
fun GameRoute(
    code: String,
    vm: GameViewModel,
    onExit: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var listening by remember { mutableStateOf(false) }
    var speechBusy by remember { mutableStateOf(false) }
    var speechAvailable by remember { mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context)) }
    var speech by remember { mutableStateOf<SpeechManager?>(null) }

    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val manager = SpeechManager(context.applicationContext, object : SpeechManager.Listener {
                override fun onRecognized(texts: List<String>) {
                    listening = false
                    vm.onSpoken(texts)
                }

                override fun onNotRecognized(message: String) {
                    listening = false
                    vm.onSpeechMessage(message)
                }

                override fun onListeningStarted() {
                    listening = true
                }

                override fun onListeningStopped() {
                    listening = false
                }

                override fun onBusyChanged(busy: Boolean) {
                    speechBusy = busy
                }
            })
            speech = manager
        } else {
            speechAvailable = false
        }
        onDispose { speech?.destroy(); speech = null }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    fun startListening() {
        if (!speechAvailable) {
            vm.onSpeechMessage("Speech recognition isn't available on this device")
            return
        }
        if (speechBusy) {
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        speech?.startListening()
    }

    BackHandler { vm.leaveMatch(); onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TopBar(code = code, onExit = { vm.leaveMatch(); onExit() })

        if (state.finished) {
            GameOverPanel(state = state, onExit = { vm.leaveMatch(); onExit() })
        } else {
            ClockCard(
                title = if (state.myIsPlayer1) "OPPONENT" else "YOU",
                time = ClockEngine.formatClock(state.oppTimeMs),
                active = state.room?.isActive(
                    if (state.myIsPlayer1) state.room?.player2Id.orEmpty() else state.room?.player1Id.orEmpty()
                ) == true && state.playing
            )

            Box {
                PlayerImage(state.currentPlayer?.displayUrl)

                if (state.currentPlayer != null && state.playing) {
                    GuessLine(
                        text = if (state.myTurn) "Guess who's on the left — hold the mic and say it" else "Opponent is guessing…",
                        highlight = state.myTurn,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    )
                }
            }

            state.feedback?.let { fb ->
                FeedbackChip(text = fb.text, correct = fb.correct)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToSpeakButton(
                    interactive = state.playing && state.myTurn,
                    listening = listening,
                    busy = speechBusy,
                    start = ::startListening,
                    stop = { speech?.stopListening() }
                )
                SkipButton(
                    enabled = state.playing && state.myTurn,
                    penaltySeconds = state.room?.penaltySeconds ?: 10L,
                    onSkip = vm::onSkip
                )
            }

            ClockCard(
                title = if (state.myIsPlayer1) "YOU" else "OPPONENT",
                time = ClockEngine.formatClock(state.myTimeMs),
                active = state.myTurn && state.playing
            )
        }
    }

    LaunchedEffect(state.feedback) {
        if (state.feedback != null) {
            delay(1700)
            vm.clearFeedback()
        }
    }
}

@Composable
private fun TopBar(code: String, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ROOM $code",
            style = MaterialTheme.typography.labelLarge,
            color = IceBlue
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "Leave", tint = OffWhite)
        }
    }
}

@Composable
private fun ClockCard(title: String, time: String, active: Boolean) {
    val low = time.startsWith("00:") && time != "00:00"
    val timeColor = when {
        low -> Danger
        active -> Orange
        else -> OffWhite
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (active) SurfaceRaised else SurfacePanel,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = if (active) Orange else SurfaceRaised
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = IceBlue,
                    fontWeight = FontWeight.Bold
                )
                if (active) {
                    Text(
                        text = "● LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Orange
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = time,
                style = MaterialTheme.typography.displaySmall,
                color = timeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlayerImage(url: String?) {
    val painter = rememberAsyncImagePainter(
        model = url,
        placeholder = ColorPainter(SurfaceRaised),
        error = ColorPainter(SurfaceRaised)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceRaised
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun GuessLine(text: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) Orange else SurfacePanel.copy(alpha = 0.92f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (highlight) Color.Black else OffWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FeedbackChip(text: String, correct: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (correct) Success.copy(alpha = 0.18f) else SurfacePanel,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = if (correct) Success else Danger
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (correct) Success else OffWhite,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HoldToSpeakButton(
    interactive: Boolean,
    listening: Boolean,
    busy: Boolean,
    start: () -> Unit,
    stop: () -> Unit
) {
    var holding by remember { mutableStateOf(false) }

    val hasControl = interactive && !busy
    val scale = if (listening || holding) 1.12f else 1f
    Box(
        modifier = Modifier
            .size(96.dp)
            .pointerInput(interactive) {
                awaitEachGesture {
                    if (!interactive) return@awaitEachGesture
                    val down = awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    start()
                    waitForUpOrCancellation()
                    holding = false
                    stop()
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (hasControl) Orange else SurfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.KeyboardVoice,
                contentDescription = "Hold to speak",
                tint = if (hasControl) Color.Black else OffWhite.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (busy) "Busy…" else if (listening) "Listening…" else "Hold",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasControl) Color.Black else OffWhite.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SkipButton(enabled: Boolean, penaltySeconds: Long, onSkip: () -> Unit) {
    OutlinedButton(
        onClick = onSkip,
        enabled = enabled,
        modifier = Modifier
            .height(96.dp)
            .padding(horizontal = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SKIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("−${penaltySeconds}s", style = MaterialTheme.typography.labelSmall, color = Danger)
        }
    }
}

@Composable
private fun GameOverPanel(state: GameViewModel.UiState, onExit: () -> Unit) {
    val youWon = state.iWon == true
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfacePanel
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (youWon) "YOU WIN! 🏆" else "TIME'S UP",
                style = MaterialTheme.typography.headlineLarge,
                color = if (youWon) Success else Danger,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (youWon) "Your shot clock never ran out." else "${state.currentPlayer?.name ?: "Your opponent"} outlasted you.",
                style = MaterialTheme.typography.bodyMedium,
                color = OffWhite
            )
            Button(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Back to Home", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}