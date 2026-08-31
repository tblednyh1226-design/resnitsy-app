package ru.slotelly.app.data

import com.google.gson.JsonObject

class NativeSettingsExtras(private val api: SlotellyApi = SlotellyApi.create()) {
    suspend fun setOnlineBooking(pin:String, enabled:Boolean): JsonObject =
        api.setOnlineBooking(mapOf("p_pin" to pin, "p_enabled" to enabled))

    suspend fun patchSettings(pin:String, patch:Map<String,Any?>): JsonObject =
        api.settingsPatch(mapOf("p_pin" to pin, "p_patch" to patch))

    suspend fun notificationsGet(pin:String): JsonObject =
        api.notifications(mapOf("pin" to pin, "action" to "get"))

    suspend fun notificationsSave(pin:String, settings:Map<String,Any?>): JsonObject =
        api.notifications(mapOf("pin" to pin, "action" to "save", "settings" to settings))

    suspend fun notificationsJournal(pin:String, status:String="all", limit:Int=50): JsonObject =
        api.notifications(mapOf("pin" to pin, "action" to "journal", "status" to status, "limit" to limit))
}
