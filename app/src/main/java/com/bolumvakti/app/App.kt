package com.bolumvakti.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bolumvakti.app.data.Repo
import com.bolumvakti.app.work.RefreshWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Yeni bölümler", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Takip ettiğin dizilerin yeni bölümleri internete düştüğünde"
            })
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(2, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }
    companion object { const val CHANNEL = "new_episodes" }
}
