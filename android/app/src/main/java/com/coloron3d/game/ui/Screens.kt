package com.coloron3d.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coloron3d.game.data.DAILY_REWARDS
import com.coloron3d.game.data.MISSIONS
import com.coloron3d.game.data.Progress
import com.coloron3d.game.data.SKINS
import com.coloron3d.game.engine.GameSnapshot
import kotlinx.coroutines.delay

val Palette = listOf(Color(0xFFFF4571), Color(0xFFFFD145), Color(0xFF8260F6))
private val Panel = Color(0xCC1A1033)
private val Gold = Color(0xFFFFD145)

@Composable
fun Logo() {
    Row {
        "COLORON".forEachIndexed { i, ch ->
            Text(ch.toString(), color = Palette[i % 3], fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        Text(" 3D", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CoinBar(p: Progress) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(140.dp)) {
            Text("Nível ${p.level}", color = Color.White, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { p.levelProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Palette[2],
            )
        }
        Pill("🪙 ${p.coins}")
    }
}

@Composable
fun Pill(text: String, color: Color = Panel) {
    Box(Modifier.clip(CircleShape).background(color).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)).padding(14.dp),
        content = content,
    )
}

@Composable
fun BigButton(text: String, color: Color = Palette[0], onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        1f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s",
    )
    Button(
        onClick, Modifier.fillMaxWidth().height(64.dp).scale(pulse),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(32.dp),
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
fun MenuScreen(p: Progress, vm: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
        CoinBar(p)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Logo()
            Text("Recorde: ${p.best}", color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(140.dp)) // deixa a cena 3D aparecer
            BigButton("JOGAR") { vm.play() }

            // Calendário de recompensa diária
            Card {
                Text("Recompensa diária", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                val next = p.nextDailyIndex()
                val canClaim = p.canClaimDaily()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAILY_REWARDS.forEachIndexed { i, r ->
                        val done = if (canClaim) i < next else i < p.streak
                        val isToday = canClaim && i == next
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(when { done -> Palette[2]; isToday -> Gold; else -> Color(0x22FFFFFF) })
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("D${i + 1}", color = if (isToday) Color.Black else Color.White, fontSize = 11.sp)
                            Text(if (i == 6) "🎁" else "🪙", fontSize = 14.sp)
                            Text("$r", color = if (isToday) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    { vm.claimDaily() }, Modifier.fillMaxWidth(), enabled = canClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                ) { Text(if (canClaim) "Resgatar" else "Volte amanhã! 🔥 ${p.streak} dias") }
            }

            // Baú grátis
            Card {
                val left = p.chestReadyIn(now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💎", fontSize = 36.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Baú grátis", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (left == 0L) "Pronto para abrir!" else "Abre em ${formatTime(left)}",
                            color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                    }
                    Button({ vm.openChest() }, enabled = left == 0L,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette[0])) { Text("Abrir") }
                }
            }

            // Missões diárias
            Card {
                Text("Missões de hoje", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val day = p.today()
                val prog = p.missionsFor(day)
                val claimed = p.claimedFor(day)
                MISSIONS.forEach { m ->
                    val v = prog[m.id].coerceAtMost(m.goal)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = Color.White, fontSize = 14.sp)
                            LinearProgressIndicator(
                                progress = { v / m.goal.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(CircleShape),
                                color = Palette[1],
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        when {
                            m.id in claimed -> Pill("✔")
                            v >= m.goal -> Button({ vm.claimMission(m.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text("+${m.reward}") }
                            else -> Pill("$v/${m.goal}")
                        }
                    }
                }
            }

            Button({ vm.openShop() }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette[2])) { Text("🛒 Loja de skins") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HudScreen(s: GameSnapshot, onColor: (Int) -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("❤".repeat(s.lives.coerceAtLeast(0)) + "♡".repeat((3 - s.lives).coerceAtLeast(0)),
                color = Palette[0], fontSize = 26.sp)
            Text("💎 ${s.gems}", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${s.score}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
            AnimatedVisibility(s.combo >= 3, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Text("COMBO x${s.combo}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Próxima cor: ", color = Color.White)
                Box(Modifier.size(22.dp).clip(CircleShape).background(Palette[s.ballColor]))
            }
        }
        // Botões de cor: pinte a próxima plataforma com a cor da bola
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Palette.forEachIndexed { i, c ->
                Box(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(c, c.copy(alpha = .6f))))
                        .border(3.dp, if (i == s.ballColor) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
                        .clickable { onColor(i) },
                )
            }
        }
    }
}

@Composable
fun GameOverScreen(s: GameSnapshot, p: Progress, earned: Int, record: Boolean, vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().background(Color(0x99000000)).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (record) Text("🏆 NOVO RECORDE!", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("${s.score}", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black)
                Text(grade(s.score), color = Palette[1], fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Melhor combo: ${s.bestCombo}   •   Gemas: ${s.gems}", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Pill("+$earned 🪙", Palette[2])
                Spacer(Modifier.height(4.dp))
                Text("Recorde: ${p.best}", color = Color.White.copy(alpha = .7f))
            }
        }
        Spacer(Modifier.height(20.dp))
        BigButton("JOGAR DE NOVO") { vm.play() }
        Spacer(Modifier.height(12.dp))
        Button({ vm.goMenu() }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Menu e recompensas") }
    }
}

@Composable
fun ShopScreen(p: Progress, vm: GameViewModel) {
    Column(Modifier.fillMaxSize().background(Color(0x88000000)).systemBarsPadding()) {
        CoinBar(p)
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Skins da bola", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            SKINS.forEach { skin ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(
                            Brush.radialGradient(listOf(Color.White, Palette[skin.id % 3]))))
                        Spacer(Modifier.width(12.dp))
                        Text(skin.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val owned = skin.id in p.owned
                        val selected = skin.id == p.selectedSkin
                        Button(
                            { vm.buyOrSelect(skin) },
                            enabled = !selected && (owned || p.coins >= skin.price),
                            colors = ButtonDefaults.buttonColors(containerColor = if (owned) Palette[2] else Gold, contentColor = if (owned) Color.White else Color.Black),
                        ) { Text(when { selected -> "Equipada"; owned -> "Equipar"; else -> "🪙 ${skin.price}" }) }
                    }
                }
            }
            Button({ vm.goMenu() }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette[0])) { Text("Voltar") }
        }
    }
}

@Composable
fun RewardToast(text: String?, onDone: () -> Unit) {
    LaunchedEffect(text) { if (text != null) { delay(2200); onDone() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text != null, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Gold).clickable { onDone() }.padding(24.dp)) {
                Text(text ?: "", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

fun grade(score: Int) = when {
    score > 60 -> "Lendário!"
    score > 40 -> "Imparável!"
    score > 25 -> "Incrível!"
    score > 15 -> "Ótimo!"
    score > 8 -> "Bom trabalho!"
    else -> "Tente de novo!"
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
