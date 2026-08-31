package ru.slotelly.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.room.Room
import androidx.work.*
import ru.slotelly.app.data.SlotellyDatabase
import ru.slotelly.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class SlotellyApp : Application() {
    val db by lazy {
        Room.databaseBuilder(this, SlotellyDatabase::class.java, "slotelly.db")
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun onCreate() {
        super.onCreate()
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("slotelly-sync", ExistingPeriodicWorkPolicy.KEEP, periodic)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val once = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build()
                WorkManager.getInstance(this@SlotellyApp).enqueueUniqueWork("slotelly-resume-sync", ExistingWorkPolicy.REPLACE, once)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }
}
