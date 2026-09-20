package com.nbarumble.game.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbarumble.game.NbaRumbleApp
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.ui.RoomCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_LOBBY = "lobby"
        const val ROUTE_GAME = "game"
        const val ROUTE_SINGLE = "single"
        const val ROUTE_LEADERBOARD = "leaderboard"
    }

    enum class ConnState { CONNECTING, CONNECTED, ERROR }

    data class UiState(
        val conn: ConnState = ConnState.CONNECTING,
        val message: String? = null,
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val bankSec: String = Difficulty.MEDIUM.defaultBankSeconds.toString(),
        val penaltySec: String = Difficulty.MEDIUM.defaultPenaltySeconds.toString(),
        val joinCode: String = "",
        val busy: Boolean = false,
        val navigateTo: String? = null
    )

    private val container = (application as NbaRumbleApp).container
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect() {
        val res = container.authRepository.ensureSignedIn()
        _ui.update {
            it.copy(
                conn = if (res.isSuccess) ConnState.CONNECTED else ConnState.ERROR,
                message = failureMessage(res)
            )
        }
    }

    fun setBank(value: String) = _ui.update { it.copy(bankSec = value.filter(Char::isDigit).take(4)) }
    fun setPenalty(value: String) = _ui.update { it.copy(penaltySec = value.filter(Char::isDigit).take(3)) }

    /** Picking a difficulty also snaps the clock presets to sensible defaults. */
    fun setDifficulty(difficulty: Difficulty) = _ui.update {
        it.copy(
            difficulty = difficulty,
            bankSec = difficulty.defaultBankSeconds.toString(),
            penaltySec = difficulty.defaultPenaltySeconds.toString()
        )
    }
    fun setJoinCode(value: String) = _ui.update { it.copy(joinCode = RoomCode.sanitize(value)) }
    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun consumeNavigation() = _ui.update { it.copy(navigateTo = null) }
    fun openSingle() = _ui.update { it.copy(navigateTo = ROUTE_SINGLE) }
    fun openLeaderboard() = _ui.update { it.copy(navigateTo = ROUTE_LEADERBOARD) }

    fun createRoom() {
        val state = _ui.value
        if (state.busy) return
        val bank = state.bankSec.toLongOrNull()?.takeIf { it > 0 } ?: 180L
        val penalty = state.penaltySec.toLongOrNull()?.takeIf { it >= 0 } ?: 10L
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val signIn = container.authRepository.ensureSignedIn()
            if (signIn.isFailure) {
                _ui.update { it.copy(busy = false, message = failureMessage(signIn)) }
                return@launch
            }
            val code = RoomCode.generate()
            val uid = container.authRepository.uid.orEmpty()
            val res = container.roomRepository.createRoom(code, uid, bank, penalty, state.difficulty.name)
            _ui.update {
                if (res.isSuccess) {
                    it.copy(busy = false, navigateTo = "$ROUTE_LOBBY/$code/true")
                } else {
                    it.copy(busy = false, message = "Couldn't create the room. ${res.exceptionOrNull()?.message.orEmpty()}")
                }
            }
        }
    }

    fun joinRoom() {
        val state = _ui.value
        if (state.busy || state.joinCode.length != 6) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val signIn = container.authRepository.ensureSignedIn()
            if (signIn.isFailure) {
                _ui.update { it.copy(busy = false, message = failureMessage(signIn)) }
                return@launch
            }
            val exists = container.roomRepository.roomExists(state.joinCode)
            _ui.update {
                if (exists) {
                    it.copy(busy = false, navigateTo = "$ROUTE_LOBBY/${state.joinCode}/false")
                } else {
                    it.copy(busy = false, message = "No room with code ${state.joinCode} — check the code.")
                }
            }
        }
    }

    private fun failureMessage(res: Result<*>): String? = when {
        res.isSuccess -> null
        else -> "Couldn't reach Firebase. Make sure google-services.json is replaced (see SETUP.md). " +
            res.exceptionOrNull()?.message.orEmpty()
    }
}