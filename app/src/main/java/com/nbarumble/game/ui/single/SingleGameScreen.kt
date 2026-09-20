package com.nbarumble.game.ui.single

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nbarumble.game.speech.SpeechManager
import com.nbarumble.game.ui.theme.Danger
import com.nbarumble.game.ui.theme.IceBlue
import com.nbarumble.game.ui.theme.OffWhite
import com.nbarumble.game.ui.theme.Orange
import com.nbarumble.game.ui.theme.SurfacePanel
import com.nbarumble.game.ui.theme.SurfaceRaised
import com.nbarumble.game.ui.theme.Success
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun SingleGameScreen(
    vm: SingleViewModel,
    state: SingleViewModel.UiState
) {
    val level = state.selectedLevel ?: return

    BackHandler { vm.backToLevels() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GameTopBar(
            level = level,
            score = state.score,
            combo = state.combo,
            onBack = vm::backToLevels
        )

        when {
            state.loading -> LoadingPanel()
            state.gameOver -> GameOverPanel(
                state = state,
                onPlayAgain = vm::retryLevel,
                onBackToLevels = vm::backToLevels
            )
            else -> {
                StatsBar(
                    level = level,
                    livesLeft = state.livesLeft,
                    secondsLeft = state.secondsLeft,
                    skipsLeft = state.skipsLeft,
                    questionCount = state.questionCount
                )

                Box {
                    PlayerImage(state.currentPlayer?.displayUrl)

                    state.currentPlayer?.let {
                        GuessLine(
                            text = "Guess who's on the screen — hold the mic and say the name",
                            highlight = true,
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
                    MicButton(
                        interactive = true,
                        onRecognized = vm::onSpoken,
                        onNotRecognized = vm::onSpeechMessage
                    )
                    SkipButton(
                        enabled = state.skipsLeft > 0,
                        skipsLeft = state.skipsLeft,
                        onSkip = vm::onSkip
                    )
                }
            }
        }
    }

    LaunchedEffect(state.feedback?.id) {
        if (state.feedback != null) {
            delay(1700)
            vm.clearFeedback()
        }
    }
}

@Composable
private fun GameTopBar(level: SingleLevel, score: Int, combo: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to levels", tint = OffWhite)
        }
        Text(
            text = level.label.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Orange,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall,
                color = IceBlue
            )
            Text(
                text = formatScore(score.toLong()),
                style = MaterialTheme.typography.headlineSmall,
                color = OffWhite,
                fontWeight = FontWeight.Black
            )
            if (combo >= 2) {
                Text(
                    text = "COMBO x$combo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Orange,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatsBar(
    level: SingleLevel,
    livesLeft: Int,
    secondsLeft: Long,
    skipsLeft: Int,
    questionCount: Int
) {
    val low = secondsLeft <= 5L
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (low) SurfaceRaised else SurfacePanel,
        border = BorderStroke(2.dp, if (low) Danger else SurfaceRaised)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIVES",
                    style = MaterialTheme.typography.labelMedium,
                    color = IceBlue,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(level.lives) { index ->
                        Icon(
                            imageVector = if (index < livesLeft) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (index < livesLeft) Danger else SurfaceRaised,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "SKIPS LEFT: $skipsLeft · Q$questionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = OffWhite
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TIME",
                    style = MaterialTheme.typography.labelMedium,
                    color = IceBlue,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatClock(secondsLeft),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (low) Danger else Orange,
                    fontWeight = FontWeight.Bold
                )
            }
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
            .aspectRatio(1f),
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
        border = BorderStroke(
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
private fun MicButton(
    interactive: Boolean,
    onRecognized: (List<String>) -> Unit,
    onNotRecognized: (String) -> Unit
) {
    val context = LocalContext.current

    var listening by remember { mutableStateOf(false) }
    var speechBusy by remember { mutableStateOf(false) }
    var speechAvailable by remember {
        mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context))
    }
    var speech by remember { mutableStateOf<SpeechManager?>(null) }

    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val manager = SpeechManager(context.applicationContext, object : SpeechManager.Listener {
                override fun onRecognized(texts: List<String>) {
                    listening = false
                    onRecognized(texts)
                }

                override fun onNotRecognized(message: String) {
                    listening = false
                    onNotRecognized(message)
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
            onNotRecognized("Speech recognition isn't available on this device")
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

    HoldToSpeakButton(
        interactive = interactive,
        listening = listening,
        busy = speechBusy,
        start = ::startListening,
        stop = { speech?.stopListening() }
    )
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
private fun SkipButton(enabled: Boolean, skipsLeft: Int, onSkip: () -> Unit) {
    OutlinedButton(
        onClick = onSkip,
        enabled = enabled,
        modifier = Modifier
            .height(96.dp)
            .padding(horizontal = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SKIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${skipsLeft} left", style = MaterialTheme.typography.labelSmall, color = Orange)
        }
    }
}

@Composable
private fun LoadingPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfacePanel
    ) {
        Text(
            text = "Loading players…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            style = MaterialTheme.typography.titleMedium,
            color = OffWhite,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GameOverPanel(
    state: SingleViewModel.UiState,
    onPlayAgain: () -> Unit,
    onBackToLevels: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfacePanel
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GAME OVER",
                style = MaterialTheme.typography.headlineLarge,
                color = Danger,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Final score",
                style = MaterialTheme.typography.bodyMedium,
                color = OffWhite
            )
            Text(
                text = formatScore(state.finalScore.toLong()),
                style = MaterialTheme.typography.displayLarge,
                color = Orange,
                fontWeight = FontWeight.Black
            )
            if (state.isNewBest) {
                Text(
                    text = "NEW BEST! 🏆",
                    style = MaterialTheme.typography.titleMedium,
                    color = Success,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Play again", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBackToLevels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Text("Back to levels", color = Orange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatClock(seconds: Long): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"