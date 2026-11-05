package com.dizicambazi.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dizicambazi.app.App
import com.dizicambazi.app.R
import com.dizicambazi.app.data.Episode
import com.dizicambazi.app.data.Repo
import com.dizicambazi.app.ui.MainActivity

class RefreshWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        return try {
            val fresh = Repo.refresh()
            notify(applicationContext, fresh.filter { it.series in Repo.follows.value })
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        fun notify(ctx: Context, eps: List<Episode>) {
            if (eps.isEmpty()) return
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            eps.take(5).forEach { e ->
                val intent = Intent(ctx, MainActivity::class.java).putExtra("episode", e.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pi = PendingIntent.getActivity(ctx, e.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(ctx, App.CHANNEL).setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("${e.seriesName} · ${e.num}. bölüm")
                    .setContentText("Yeni bölüm internete düştü. İzlemek için dokun.")
                    .setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                try { nm.notify(e.id.hashCode(), n) } catch (_: SecurityException) {}
            }
        }
    }
}
