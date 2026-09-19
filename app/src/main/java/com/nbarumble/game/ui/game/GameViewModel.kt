package com.nbarumble.game.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbarumble.game.NbaRumbleApp
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.data.model.NbaPlayer
import com.nbarumble.game.data.model.Room
import com.nbarumble.game.data.repo.RoomRepository
import com.nbarumble.game.game.ClockEngine
import com.nbarumble.game.game.FuzzyMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel(
    application: Application,
    private val roomCode: String
) : AndroidViewModel(application) {

    data class Feedback(
        val text: String,
        val correct: Boolean,
        val id: Long
    )

    data class UiState(
        val myUid: String = "",
        val room: Room? = null,
        val currentPlayer: NbaPlayer? = null,
        val myTimeMs: Long = 0L,
        val oppTimeMs: Long = 0L,
        val myTurn: Boolean = false,
        val myIsPlayer1: Boolean = true,
        val playing: Boolean = false,
        val finished: Boolean = false,
        val iWon: Boolean? = null,
        val feedback: Feedback? = null,
        val serverDown: Boolean = false
    )

    private val container = (application as NbaRumbleApp).container
    private val repo: RoomRepository = container.roomRepository
    private val matcher = FuzzyMatcher()

    private val players = MutableStateFlow<List<NbaPlayer>>(emptyList())
    private var room = MutableStateFlow<Room?>(null)
    private var feedbackCounter = 0L

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    @Volatile
    private var mutationInFlight = false

    init {
        viewModelScope.launch {
            repo.observeRoom(roomCode).collect { updated ->
                room.value = updated
                if (updated != null) {
                    players.value =
                        container.nbaPlayerRepository.players(Difficulty.fromDb(updated.difficulty))
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                draw()
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private fun draw() {
        val r = room.value ?: return
        val myUid = container.authRepository.uid.orEmpty()
        if (myUid.isNotEmpty()) {
            maybeFinishOnTimeout(r, myUid)
        }
        val now = repo.nowServer()
        val oppUid = if (r.player1Id == myUid) r.player2Id else r.player1Id
        _ui.update { state ->
            state.copy(
                myUid = if (state.myUid.isNotEmpty()) state.myUid else myUid,
                room = r,
                currentPlayer = players.value.firstOrNull { it.name == r.currentNbaPlayer },
                myTimeMs = ClockEngine.remainingMs(r, myUid, now),
                oppTimeMs = oppUid?.let { ClockEngine.remainingMs(r, it, now) } ?: 0L,
                myTurn = r.isActive(myUid),
                myIsPlayer1 = r.player1Id == myUid,
                playing = r.isPlaying,
                finished = r.isFinished,
                iWon = if (r.isFinished) r.winnerId == myUid else null
            )
        }
    }

    /** If the active player's live clock hit zero, ask the server to finish (idempotent). */
    private fun maybeFinishOnTimeout(r: Room, myUid: String) {
        if (!r.isPlaying || mutationInFlight) return
        val now = repo.nowServer()
        val active = r.activePlayerId ?: return
        val remaining = ClockEngine.remainingMs(r, active, now)
        if (remaining > 0L) return
        if (active == myUid || r.opponentOfActive() == myUid) {
            mutationInFlight = true
            viewModelScope.launch {
                val res = repo.finishOnTimeout(roomCode, active)
                if (res.isFailure) {
                    postError("Couldn't reach the server")
                }
                mutationInFlight = false
            }
        }
    }

    /** Called with the live transcription while holding the mic. */
    fun onSpoken(texts: List<String>) {
        val state = _ui.value
        val r = state.room ?: return
        val player = state.currentPlayer ?: return
        if (!r.isPlaying || !r.isActive(state.myUid)) return

        val result = matcher.matchAny(texts, player)
        if (result.isMatch) {
            postFeedback("Correct! ${player.name}", correct = true)
            submitGuess()
        } else {
            postFeedback("Not quite — it's still your turn", correct = false)
        }
    }

    fun onSkip() {
        val state = _ui.value
        val r = state.room ?: return
        if (!r.isPlaying || !r.isActive(state.myUid)) return
        if (mutationInFlight) return

        mutationInFlight = true
        viewModelScope.launch {
            val res = repo.skip(roomCode, state.myUid, players.value)
            mutationInFlight = false
            if (res.isFailure) {
                postError("Couldn't reach the server")
            }
        }
    }

    fun clearFeedback() {
        _ui.update { it.copy(feedback = null) }
    }

    /** Transient speech-service message, e.g. "Didn't catch that". */
    fun onSpeechMessage(text: String) {
        _ui.update { it.copy(feedback = Feedback(text, false, feedbackCounter++)) }
    }

    /** Best-effort cleanup once the match is over. */
    fun leaveMatch() {
        val r = room.value
        if (r?.isFinished == true || r?.isWaiting == true) {
            viewModelScope.launch { repo.deleteRoom(roomCode) }
        }
    }

    private fun submitGuess() {
        val myUid = _ui.value.myUid
        if (mutationInFlight) return
        mutationInFlight = true
        viewModelScope.launch {
            val res = repo.reportCorrectGuess(roomCode, myUid, players.value)
            mutationInFlight = false
            if (res.isFailure) {
                postError("Couldn't reach the server")
            }
        }
    }

    private fun postError(text: String) {
        _ui.update { it.copy(feedback = Feedback(text, false, feedbackCounter++), serverDown = true) }
    }

    private fun postFeedback(text: String, correct: Boolean) {
        _ui.update { it.copy(feedback = Feedback(text, correct, feedbackCounter++)) }
    }

    private fun Room.opponentOfActive(): String? {
        val active = activePlayerId ?: return null
        return if (player1Id == active) player2Id else player1Id
    }
}