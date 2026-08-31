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

    suspend fun dayoffs(pin:String, from:String, to:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "list", "from" to from, "to" to to))

    suspend fun setDayoff(pin:String, date:String, force:Boolean=false): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "set", "date" to date, "force" to force))

    suspend fun unsetDayoff(pin:String, date:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "unset", "date" to date))

    suspend fun setWorkdayOverride(pin:String, date:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "setWorkdayOverride", "date" to date))

    suspend fun unsetWorkdayOverride(pin:String, date:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "unsetWorkdayOverride", "date" to date))

    suspend fun breaks(pin:String, from:String, to:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "listBreaks", "from" to from, "to" to to))

    suspend fun createBreak(pin:String, date:String, start:String, end:String, label:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "createBreak", "date" to date, "start" to start, "end" to end, "label" to label))

    suspend fun deleteBreak(pin:String, id:String): JsonObject =
        api.dayoff(mapOf("pin" to pin, "action" to "deleteBreak", "id" to id))
}
