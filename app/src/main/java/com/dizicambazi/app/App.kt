package com.dizicambazi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dizicambazi.app.data.Repo
import com.dizicambazi.app.work.RefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Yeni bölümler", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Takip ettiğin dizilerin yeni bölümleri internete düştüğünde"
            })
        RefreshWorker.schedule(this, Repo.refreshHours.value)
    }
    companion object { const val CHANNEL = "new_episodes" }
}
