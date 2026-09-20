package com.nbarumble.game.ui.single

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbarumble.game.NbaRumbleApp
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.data.model.NbaPlayer
import com.nbarumble.game.game.FuzzyMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SingleLevel(
    val label: String,
    val difficulty: Difficulty,
    val league: String?,
    val secondsPerGuess: Long,
    val lives: Int,
    val subtitle: String
) {
    ROOKIE(
        label = "Rookie",
        difficulty = Difficulty.EASY,
        league = NbaPlayer.LEAGUE_NBA,
        secondsPerGuess = 60L,
        lives = 3,
        subtitle = "NBA icons only · 60s per guess · 3 lives"
    ),
    PRO(
        label = "Pro",
        difficulty = Difficulty.MEDIUM,
        league = NbaPlayer.LEAGUE_NBA,
        secondsPerGuess = 45L,
        lives = 3,
        subtitle = "NBA stars · 45s per guess · 3 lives"
    ),
    ALL_STAR(
        label = "All-Star",
        difficulty = Difficulty.HARD,
        league = NbaPlayer.LEAGUE_NBA,
        secondsPerGuess = 30L,
        lives = 2,
        subtitle = "NBA deep cuts · 30s per guess · 2 lives"
    ),
    LEGEND(
        label = "Legend",
        difficulty = Difficulty.HARD,
        league = null,
        secondsPerGuess = 25L,
        lives = 2,
        subtitle = "NBA + EuroLeague · 25s per guess · 2 lives"
    )
}

class SingleViewModel(application: Application) : AndroidViewModel(application) {

    data class Feedback(
        val text: String,
        val correct: Boolean,
        val id: Long
    )

    data class UiState(
        val selectedLevel: SingleLevel? = null,
        val loading: Boolean = false,
        val currentPlayer: NbaPlayer? = null,
        val combo: Int = 0,
        val score: Int = 0,
        val livesLeft: Int = 0,
        val skipsLeft: Int = 3,
        val secondsLeft: Long = 0L,
        val questionCount: Int = 0,
        val gameOver: Boolean = false,
        val finalScore: Int = 0,
        val isNewBest: Boolean = false,
        val feedback: Feedback? = null,
        val bestByLevel: Map<SingleLevel, Long> = emptyMap()
    )

    companion object {
        const val ROUTE_SINGLE = "single"
        private const val PREFS_NAME = "single_mode"
        private const val PREF_KEY_PREFIX = "single_best_"
        private const val DEFAULT_SKIPS = 3
    }

    private val container = (application as NbaRumbleApp).container
    private val playerRepo = container.nbaPlayerRepository
    private val matcher = FuzzyMatcher()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var pool: List<NbaPlayer> = emptyList()
    private var usedNames = mutableSetOf<String>()
    private var feedbackCounter = 0L
    private var timerJob: Job? = null

    init {
        _ui.update {
            it.copy(
                bestByLevel = SingleLevel.entries.associateWith { level ->
                    prefs.getLong(keyFor(level), 0L)
                }
            )
        }
    }

    fun onLevelSelected(level: SingleLevel) {
        if (_ui.value.loading) return
        timerJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(selectedLevel = level, loading = true) }
            pool = playerRepo.players(level.difficulty, level.league).shuffled()
            usedNames.clear()
            val first = nextPlayer()
            _ui.update {
                it.copy(
                    selectedLevel = level,
                    loading = false,
                    currentPlayer = first,
                    combo = 0,
                    score = 0,
                    livesLeft = level.lives,
                    skipsLeft = DEFAULT_SKIPS,
                    secondsLeft = level.secondsPerGuess,
                    questionCount = 0,
                    gameOver = false,
                    finalScore = 0,
                    isNewBest = false,
                    feedback = null
                )
            }
            if (first != null) {
                startTimer(level)
            } else {
                finishGame()
            }
        }
    }

    fun retryLevel() {
        _ui.value.selectedLevel?.let { onLevelSelected(it) }
    }

    fun backToLevels() {
        timerJob?.cancel()
        _ui.update {
            it.copy(
                selectedLevel = null,
                loading = false,
                currentPlayer = null,
                gameOver = false,
                feedback = null
            )
        }
    }

    /** Called with the recognition hypotheses while holding the mic. */
    fun onSpoken(texts: List<String>) {
        val state = _ui.value
        val level = state.selectedLevel ?: return
        val player = state.currentPlayer ?: return
        if (state.gameOver || state.loading) return
        if (matcher.matchAny(texts, player).isMatch) {
            onCorrect(level, player.name)
        } else {
            postFeedback("Not quite, keep trying", correct = false)
        }
    }

    fun onSkip() {
        val state = _ui.value
        val level = state.selectedLevel ?: return
        if (state.gameOver || state.loading || state.skipsLeft <= 0) return
        _ui.update { it.copy(skipsLeft = state.skipsLeft - 1) }
        advance(level)
    }

    fun clearFeedback() {
        _ui.update { it.copy(feedback = null) }
    }

    /** Transient speech-service message, e.g. "Didn't catch that". */
    fun onSpeechMessage(text: String) {
        postFeedback(text, correct = false)
    }

    private fun onCorrect(level: SingleLevel, name: String) {
        val state = _ui.value
        val newCombo = state.combo + 1
        val newScore = state.score + 100 + (newCombo / 5) * 50
        postFeedback("Correct! $name", correct = true)
        _ui.update {
            it.copy(
                combo = newCombo,
                score = newScore,
                questionCount = state.questionCount + 1
            )
        }
        advance(level)
    }

    private fun onTimeout() {
        val state = _ui.value
        val level = state.selectedLevel ?: return
        if (state.gameOver) return
        val livesLeft = state.livesLeft - 1
        postFeedback("Time's up — 1 life lost", correct = false)
        if (livesLeft <= 0) {
            _ui.update { it.copy(livesLeft = 0) }
            finishGame()
        } else {
            _ui.update { it.copy(livesLeft = livesLeft) }
            advance(level)
        }
    }

    private fun advance(level: SingleLevel) {
        val next = nextPlayer()
        if (next == null) {
            finishGame()
            return
        }
        _ui.update { it.copy(currentPlayer = next, secondsLeft = level.secondsPerGuess) }
        startTimer(level)
    }

    private fun nextPlayer(): NbaPlayer? {
        val candidates = pool.filter { it.name !in usedNames }
        if (candidates.isEmpty()) return null
        val pick = candidates.random()
        usedNames.add(pick.name)
        return pick
    }

    private fun startTimer(level: SingleLevel) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var left = level.secondsPerGuess
            while (left > 0) {
                _ui.update { it.copy(secondsLeft = left) }
                delay(1000)
                left--
            }
            if (!_ui.value.gameOver) onTimeout()
        }
    }

    private fun finishGame() {
        timerJob?.cancel()
        val state = _ui.value
        val level = state.selectedLevel
        val oldBest = level?.let { prefs.getLong(keyFor(it), 0L) } ?: 0L
        val newBest = state.score > oldBest
        if (level != null && newBest) {
            prefs.edit().putLong(keyFor(level), state.score.toLong()).apply()
        }
        val updatedBest = if (level != null && newBest) {
            state.bestByLevel + (level to state.score.toLong())
        } else {
            state.bestByLevel
        }
        _ui.update {
            it.copy(
                gameOver = true,
                secondsLeft = 0L,
                finalScore = state.score,
                isNewBest = newBest,
                bestByLevel = updatedBest
            )
        }
    }

    private fun postFeedback(text: String, correct: Boolean) {
        _ui.update { it.copy(feedback = Feedback(text, correct, feedbackCounter++)) }
    }

    private fun keyFor(level: SingleLevel): String = PREF_KEY_PREFIX + level.name

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}