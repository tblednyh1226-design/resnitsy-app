package ru.slotelly.app.sync

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.slotelly.app.SlotellyApp
import ru.slotelly.app.data.SlotellyRepository

val Context.dataStore by preferencesDataStore("slotelly")
val PIN_KEY=stringPreferencesKey("master_pin")
class SyncWorker(ctx:Context,params:WorkerParameters):CoroutineWorker(ctx,params){override suspend fun doWork():Result{val pin=applicationContext.dataStore.data.first()[PIN_KEY]?:return Result.success();return try{SlotellyRepository((applicationContext as SlotellyApp).db).sync(pin);Result.success()}catch(_:Exception){Result.retry()}}}
