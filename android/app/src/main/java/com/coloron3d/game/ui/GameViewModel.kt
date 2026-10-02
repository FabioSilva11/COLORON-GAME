package com.coloron3d.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coloron3d.game.data.GameResult
import com.coloron3d.game.data.Progress
import com.coloron3d.game.data.RewardsRepository
import com.coloron3d.game.data.Skin
import com.coloron3d.game.engine.GameSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { Menu, Playing, Over, Shop }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RewardsRepository(app)

    val progress: StateFlow<Progress> =
        repo.progress.stateIn(viewModelScope, SharingStarted.Eagerly, Progress())

    private val _snapshot = MutableStateFlow(GameSnapshot())
    val snapshot = _snapshot.asStateFlow()

    private val _screen = MutableStateFlow(Screen.Menu)
    val screen = _screen.asStateFlow()

    /** Mensagem de prêmio exibida em popup ("+150 moedas"). */
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    var lastEarned = 0
        private set
    var newRecord = false
        private set

    fun onSnapshot(s: GameSnapshot) {
        val prev = _snapshot.value
        _snapshot.value = s
        if (s.over && !prev.over && _screen.value == Screen.Playing) {
            newRecord = s.score > progress.value.best
            viewModelScope.launch {
                lastEarned = repo.recordGame(GameResult(s.score, s.bestCombo, s.gems))
                _screen.value = Screen.Over
            }
        }
    }

    fun play() { _screen.value = Screen.Playing }
    fun goMenu() { _screen.value = Screen.Menu }
    fun openShop() { _screen.value = Screen.Shop }
    fun dismissToast() { _toast.value = null }

    fun claimDaily() = viewModelScope.launch {
        val c = repo.claimDaily()
        if (c > 0) _toast.value = "Recompensa diária: +$c 🪙"
    }

    fun openChest() = viewModelScope.launch {
        val c = repo.openChest()
        if (c > 0) _toast.value = "Baú aberto: +$c 🪙"
    }

    fun claimMission(id: Int) = viewModelScope.launch { repo.claimMission(id) }
    fun buyOrSelect(s: Skin) = viewModelScope.launch { repo.buyOrSelect(s) }
}
