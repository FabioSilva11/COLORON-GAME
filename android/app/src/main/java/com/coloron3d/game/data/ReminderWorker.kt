package com.coloron3d.game.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coloron3d.game.MainActivity
import com.coloron3d.game.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Lembra o jogador de voltar quando o baú ou a recompensa diária estão prontos. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val p = RewardsRepository(applicationContext).progress.first()
        val text = when {
            p.canClaimDaily() && p.lastClaimDay == p.today() - 1 ->
                "🔥 Não perca sua sequência de ${p.streak} dias! Resgate o prêmio de hoje."
            p.canClaimDaily() -> "🎁 Sua recompensa diária está esperando!"
            p.chestReadyIn() == 0L -> "💎 Seu baú grátis está pronto para abrir!"
            else -> return Result.success()
        }
        notify(text)
        return Result.success()
    }

    private fun notify(text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recompensas", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Coloron 3D")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }

    companion object {
        private const val CHANNEL = "rewards"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("reminder", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
