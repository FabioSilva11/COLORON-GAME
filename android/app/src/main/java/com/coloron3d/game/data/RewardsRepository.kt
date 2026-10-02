package com.coloron3d.game.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.random.Random

private val Context.store by preferencesDataStore("coloron_progress")

data class Skin(val id: Int, val name: String, val price: Int)

val SKINS = listOf(
    Skin(0, "Clássica", 0),
    Skin(1, "Neon", 300),
    Skin(2, "Cromo", 600),
    Skin(3, "Lava", 1000),
    Skin(4, "Galáxia", 2000),
)

/** Recompensas do calendário de 7 dias (dia 7 = prêmio grande). */
val DAILY_REWARDS = listOf(50, 75, 100, 150, 200, 300, 600)
const val CHEST_COOLDOWN_MS = 4 * 60 * 60 * 1000L

data class Mission(val id: Int, val title: String, val goal: Int, val reward: Int)

val MISSIONS = listOf(
    Mission(0, "Jogue 3 partidas", 3, 60),
    Mission(1, "Faça 15 pontos numa partida", 15, 100),
    Mission(2, "Combo de 10", 10, 120),
    Mission(3, "Colete 3 gemas", 3, 150),
)

data class Progress(
    val coins: Int = 0,
    val best: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,             // dias consecutivos já resgatados (0..7, cicla)
    val lastClaimDay: Long = -1,
    val lastChestAt: Long = 0,
    val owned: Set<Int> = setOf(0),
    val selectedSkin: Int = 0,
    val missionDay: Long = -1,
    val missionProgress: List<Int> = List(MISSIONS.size) { 0 },
    val missionClaimed: Set<Int> = emptySet(),
) {
    val level get() = 1 + xp / 500
    val levelProgress get() = (xp % 500) / 500f
    fun today() = LocalDate.now().toEpochDay()
    fun canClaimDaily() = lastClaimDay != today()
    /** Índice do dia (0..6) que será resgatado agora; streak zera se perdeu um dia. */
    fun nextDailyIndex() = if (lastClaimDay == today() - 1) streak % 7 else 0
    fun chestReadyIn(now: Long = System.currentTimeMillis()) =
        (lastChestAt + CHEST_COOLDOWN_MS - now).coerceAtLeast(0)
    fun missionsFor(day: Long) = if (missionDay == day) missionProgress else List(MISSIONS.size) { 0 }
    fun claimedFor(day: Long) = if (missionDay == day) missionClaimed else emptySet()
}

data class GameResult(val score: Int, val bestCombo: Int, val gems: Int)

class RewardsRepository(private val context: Context) {
    private object K {
        val coins = intPreferencesKey("coins")
        val best = intPreferencesKey("best")
        val xp = intPreferencesKey("xp")
        val streak = intPreferencesKey("streak")
        val lastClaim = longPreferencesKey("last_claim")
        val lastChest = longPreferencesKey("last_chest")
        val owned = stringPreferencesKey("owned")
        val skin = intPreferencesKey("skin")
        val missionDay = longPreferencesKey("mission_day")
        val missionProg = stringPreferencesKey("mission_prog")
        val missionClaimed = stringPreferencesKey("mission_claimed")
    }

    val progress: Flow<Progress> = context.store.data.map { it.toProgress() }

    private fun Preferences.toProgress() = Progress(
        coins = this[K.coins] ?: 0,
        best = this[K.best] ?: 0,
        xp = this[K.xp] ?: 0,
        streak = this[K.streak] ?: 0,
        lastClaimDay = this[K.lastClaim] ?: -1,
        lastChestAt = this[K.lastChest] ?: 0,
        owned = (this[K.owned] ?: "0").split(',').mapNotNull { it.toIntOrNull() }.toSet(),
        selectedSkin = this[K.skin] ?: 0,
        missionDay = this[K.missionDay] ?: -1,
        missionProgress = (this[K.missionProg] ?: "").split(',').mapNotNull { it.toIntOrNull() }
            .let { if (it.size == MISSIONS.size) it else List(MISSIONS.size) { 0 } },
        missionClaimed = (this[K.missionClaimed] ?: "").split(',').mapNotNull { it.toIntOrNull() }.toSet(),
    )

    /** Resgata a recompensa diária. Retorna moedas ganhas (0 se já resgatou hoje). */
    suspend fun claimDaily(): Int {
        var gained = 0
        context.store.edit { p ->
            val pr = p.toProgress()
            if (!pr.canClaimDaily()) return@edit
            val idx = pr.nextDailyIndex()
            gained = DAILY_REWARDS[idx]
            p[K.coins] = pr.coins + gained
            p[K.streak] = idx + 1
            p[K.lastClaim] = pr.today()
        }
        return gained
    }

    /** Baú grátis a cada 4h com prêmio aleatório. */
    suspend fun openChest(): Int {
        var gained = 0
        context.store.edit { p ->
            val pr = p.toProgress()
            if (pr.chestReadyIn() > 0) return@edit
            gained = Random.nextInt(4, 25) * 5 + 10 * pr.level
            p[K.coins] = pr.coins + gained
            p[K.lastChest] = System.currentTimeMillis()
        }
        return gained
    }

    /** Registra partida: moedas, XP, recorde e missões. Retorna moedas ganhas. */
    suspend fun recordGame(r: GameResult): Int {
        val gained = r.score * 2 + r.bestCombo + r.gems * 25
        context.store.edit { p ->
            val pr = p.toProgress()
            val day = pr.today()
            val prog = pr.missionsFor(day).toMutableList()
            prog[0] += 1
            prog[1] = maxOf(prog[1], r.score)
            prog[2] = maxOf(prog[2], r.bestCombo)
            prog[3] += r.gems
            p[K.missionDay] = day
            p[K.missionProg] = prog.joinToString(",")
            p[K.missionClaimed] = pr.claimedFor(day).joinToString(",")
            p[K.coins] = pr.coins + gained
            p[K.xp] = pr.xp + r.score * 10 + 20
            if (r.score > pr.best) p[K.best] = r.score
        }
        return gained
    }

    suspend fun claimMission(id: Int) {
        context.store.edit { p ->
            val pr = p.toProgress()
            val day = pr.today()
            val claimed = pr.claimedFor(day)
            val m = MISSIONS[id]
            if (id in claimed || pr.missionsFor(day)[id] < m.goal) return@edit
            p[K.coins] = pr.coins + m.reward
            p[K.missionClaimed] = (claimed + id).joinToString(",")
        }
    }

    /** Compra ou equipa uma skin. */
    suspend fun buyOrSelect(skin: Skin) {
        context.store.edit { p ->
            val pr = p.toProgress()
            if (skin.id in pr.owned) {
                p[K.skin] = skin.id
            } else if (pr.coins >= skin.price) {
                p[K.coins] = pr.coins - skin.price
                p[K.owned] = (pr.owned + skin.id).joinToString(",")
                p[K.skin] = skin.id
            }
        }
    }
}
