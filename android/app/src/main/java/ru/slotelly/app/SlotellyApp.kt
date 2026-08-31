package ru.slotelly.app

import android.app.Application
import androidx.room.Room
import androidx.work.*
import ru.slotelly.app.data.SlotellyDatabase
import ru.slotelly.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class SlotellyApp : Application() {
    val db by lazy {
        Room.databaseBuilder(this, SlotellyDatabase::class.java, "slotelly.db")
            // Old native builds 0.1.x were disposable developer betas.
            // From schema v3 onward migrations must be explicit: no silent data loss.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("slotelly-sync", ExistingPeriodicWorkPolicy.KEEP, work)
    }
}
