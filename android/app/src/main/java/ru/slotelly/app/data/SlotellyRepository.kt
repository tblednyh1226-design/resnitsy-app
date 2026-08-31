package ru.slotelly.app.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import java.time.ZonedDateTime

class SlotellyRepository(
    private val db: SlotellyDatabase,
    private val api: SlotellyApi = SlotellyApi.create()
) {
    private val gson = Gson()

    fun appointments(): Flow<List<AppointmentEntity>> = db.appointments().observeActive()
    fun clients(): Flow<List<ClientEntity>> = db.clients().observeAll()
    fun services(): Flow<List<ServiceEntity>> = db.services().observeActive()

    suspend fun sync(pin: String) {
        // First try to send local user actions. Until they are confirmed, they remain authoritative locally.
        flush(pin)

        val pendingLocal = db.appointments().all().filter { it.pending }.associateBy { it.id }
        val now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
        val from = now.minusDays(21).toInstant().toString()
        val to = now.plusDays(45).toInstant().toString()
        val j = api.call(ApiEnvelope(pin, "snapshot", mapOf("from" to from, "to" to to, "include_clients" to true)))

        val serviceRows = j.getAsJsonArray("services")?.map { x ->
            val o = x.asJsonObject
            ServiceEntity(
                o["id"].asString,
                o["name"].asString,
                o["base_price"]?.asDouble ?: 0.0,
                o["duration_minutes"]?.asInt ?: 60,
                o["is_active"]?.asBoolean ?: true
            )
        } ?: emptyList()
        db.services().upsert(serviceRows)

        val clientRows = j.getAsJsonArray("clients")?.map { x ->
            val o = x.asJsonObject
            ClientEntity(
                o["id"].asString,
                o["display_name"]?.asString ?: "Клиент",
                o["phone"]?.asString ?: "",
                o["messenger"]?.asString ?: ""
            )
        } ?: emptyList()
        db.clients().upsert(clientRows)

        val arr = j.getAsJsonObject("calendar")?.getAsJsonArray("appointments")
        val serverRows = arr?.map { x ->
            val o = x.asJsonObject
            AppointmentEntity(
                o["id"].asString,
                o["client_id"]?.takeUnless { it.isJsonNull }?.asString,
                o["client_name"]?.asString ?: "Клиент",
                o["starts_at"].asString,
                o["ends_at"].asString,
                o["status"]?.asString ?: "new",
                gson.toJson(o["services"]),
                gson.toJson(o["payments"]),
                false
            )
        } ?: emptyList()

        // A stale server snapshot must never resurrect an operation that is still pending on the phone.
        val merged = serverRows.map { remote -> pendingLocal[remote.id] ?: remote }.toMutableList()
        pendingLocal.values.forEach { local -> if (merged.none { it.id == local.id }) merged += local }
        db.appointments().upsert(merged)
    }

    suspend fun cancel(id: String) {
        db.appointments().setStatus(id, "cancelled")
        db.mutations().add(PendingMutationEntity(action = "set_status", payloadJson = gson.toJson(mapOf("id" to id, "status" to "cancelled"))))
    }

    suspend fun markUnpaid(id: String) {
        db.appointments().setStatus(id, "completed_unpaid")
        db.mutations().add(PendingMutationEntity(action = "set_status", payloadJson = gson.toJson(mapOf("id" to id, "status" to "completed_unpaid"))))
    }

    suspend fun flush(pin: String) {
        for (m in db.mutations().all()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val payload = gson.fromJson(m.payloadJson, Map::class.java) as Map<String, Any?>
                api.call(ApiEnvelope(pin, m.action, payload))
                val id = payload["id"]?.toString()
                if (id != null) db.appointments().clearPending(id)
                db.mutations().delete(m.localId)
            } catch (_: Exception) {
                db.mutations().fail(m.localId)
                break
            }
        }
    }
}
