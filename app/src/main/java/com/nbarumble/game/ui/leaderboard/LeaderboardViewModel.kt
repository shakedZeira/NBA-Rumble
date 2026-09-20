package com.nbarumble.game.ui.leaderboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbarumble.game.NbaRumbleApp
import com.nbarumble.game.data.model.LeaderboardEntry
import com.nbarumble.game.data.repo.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isLoading: Boolean = true,
        val entries: List<LeaderboardEntry> = emptyList(),
        val myUid: String = "",
        val error: String? = null
    )

    private val container = (application as NbaRumbleApp).container
    private val repo: LeaderboardRepository = container.leaderboardRepository
    private val _ui = MutableStateFlow(UiState(myUid = container.authRepository.uid.orEmpty()))
    val ui = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.observeTop(20)
                .catch { e ->
                    _ui.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load leaderboard") }
                }
                .collect { entries ->
                    _ui.update { it.copy(isLoading = false, entries = entries, error = null) }
                }
        }
    }

    fun retry() = refresh()
}