package com.nbarumble.game.ui.lobby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbarumble.game.NbaRumbleApp
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.data.model.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LobbyViewModel(
    application: Application,
    val roomCode: String,
    val isHost: Boolean
) : AndroidViewModel(application) {

    data class UiState(
        val code: String = "",
        val isHost: Boolean = false,
        val room: Room? = null,
        val missing: Boolean = false
    )

    private val container = (application as NbaRumbleApp).container
    private val _ui = MutableStateFlow(UiState(code = roomCode, isHost = isHost))
    val ui = _ui.asStateFlow()

    private var joinAttempted = false

    init {
        viewModelScope.launch {
            container.roomRepository.observeRoom(roomCode).collect { room ->
                if (room == null) {
                    _ui.update { it.copy(missing = true) }
                } else {
                    _ui.update { it.copy(room = room) }
                    if (!isHost) maybeJoin(room)
                }
            }
        }
    }

    /**
     * The joiner performs the actual join transaction once while the room is
     * still waiting for a second player. This flips the room to PLAYING and
     * both sides leave the lobby when they observe that change.
     */
    private suspend fun maybeJoin(room: Room) {
        if (joinAttempted || !room.isWaiting || room.player2Id != null) return
        val uid = container.authRepository.uid ?: run {
            joinAttempted = false
            return
        }
        joinAttempted = true
        val pool = runCatching {
            container.nbaPlayerRepository.players(Difficulty.fromDb(room.difficulty))
        }.getOrNull().orEmpty()
        val res = container.roomRepository.joinRoom(roomCode, uid, pool)
        if (res.isFailure) {
            joinAttempted = false
        }
    }
}