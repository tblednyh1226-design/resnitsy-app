package ru.slotelly.app.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class SlotellyRepository(
    private val db: SlotellyDatabase,
    private val api: SlotellyApi = SlotellyApi.create()
) {
    private val gson = Gson()

    fun appointments(): Flow<List<AppointmentEntity>> = db.appointments().observeActive()
    fun clients(): Flow<List<ClientEntity>> = db.clients().observeAll()
    fun services(): Flow<List<ServiceEntity>> = db.services().observeActive()
    fun blocks(): Flow<List<CalendarBlockEntity>> = db.calendarMeta().observeBlocks()
    fun overrides(): Flow<List<AvailabilityOverrideEntity>> = db.calendarMeta().observeOverrides()
    fun appState(): Flow<AppStateEntity?> = db.calendarMeta().observeState()

    suspend fun localClients(): List<ClientEntity> = db.clients().all()
    suspend fun localServices(): List<ServiceEntity> = db.services().all()

    suspend fun sync(pin: String) {
        flush(pin)
        val pendingLocal = db.appointments().all().filter { it.pending }.associateBy { it.id }
        val now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
        val from = now.minusDays(21).toInstant().toString()
        val to = now.plusDays(45).toInstant().toString()
        val j = api.call(ApiEnvelope(pin, "snapshot", mapOf("from" to from, "to" to to, "include_clients" to true)))

        val serviceRows = j.getAsJsonArray("services")?.map { x ->
            val o = x.asJsonObject
            ServiceEntity(
                id = o["id"].asString,
                name = o["name"].asString,
                price = o["base_price"]?.asDouble ?: 0.0,
                duration = o["duration_minutes"]?.asInt ?: 60,
                active = o["is_active"]?.asBoolean ?: true,
                slotType = o["slot_type"]?.asString ?: "normal",
                category = o["category"]?.asString ?: ""
            )
        } ?: emptyList()
        db.services().upsert(serviceRows)

        val clientRows = j.getAsJsonArray("clients")?.map { x ->
            val o = x.asJsonObject
            ClientEntity(
                id = o["id"].asString,
                name = o["display_name"]?.asString ?: "Клиент",
                phone = o["phone"]?.asString ?: "",
                messenger = o["messenger"]?.takeUnless { it.isJsonNull }?.asString ?: "",
                lastServicesJson = gson.toJson(o["last_services"] ?: JsonArray())
            )
        } ?: emptyList()
        db.clients().upsert(clientRows)

        val cal = j.getAsJsonObject("calendar")
        val serverRows = cal?.getAsJsonArray("appointments")?.map { x ->
            val o = x.asJsonObject
            AppointmentEntity(
                id = o["id"].asString,
                clientId = o["client_id"]?.takeUnless { it.isJsonNull }?.asString,
                clientName = o["client_name"]?.asString ?: "Клиент",
                startsAt = o["starts_at"].asString,
                endsAt = o["ends_at"].asString,
                status = o["status"]?.asString ?: "new",
                servicesJson = gson.toJson(o["services"] ?: JsonArray()),
                paymentsJson = gson.toJson(o["payments"] ?: JsonArray()),
                comment = o["master_comment"]?.takeUnless { it.isJsonNull }?.asString ?: "",
                pending = false
            )
        } ?: emptyList()

        val merged = serverRows.map { remote -> pendingLocal[remote.id] ?: remote }.toMutableList()
        pendingLocal.values.forEach { local -> if (merged.none { it.id == local.id }) merged += local }
        db.appointments().upsert(merged)

        val blockRows = cal?.getAsJsonArray("blocks")?.map { x ->
            val o = x.asJsonObject
            CalendarBlockEntity(
                id = o["id"]?.asString ?: UUID.randomUUID().toString(),
                startsAt = o["starts_at"].asString,
                endsAt = o["ends_at"].asString,
                label = o["label"]?.takeUnless { it.isJsonNull }?.asString ?: "",
                source = o["source"]?.takeUnless { it.isJsonNull }?.asString ?: ""
            )
        } ?: emptyList()
        db.calendarMeta().clearBlocks()
        db.calendarMeta().upsertBlocks(blockRows)

        val overrideRows = cal?.getAsJsonArray("overrides")?.map { x ->
            val o = x.asJsonObject
            AvailabilityOverrideEntity(
                id = o["id"]?.asString ?: UUID.randomUUID().toString(),
                slotStart = o["slot_start"].asString,
                available = o["is_available"]?.asBoolean ?: false,
                reason = o["reason"]?.takeUnless { it.isJsonNull }?.asString ?: ""
            )
        } ?: emptyList()
        db.calendarMeta().clearOverrides()
        db.calendarMeta().upsertOverrides(overrideRows)
        db.calendarMeta().upsertState(AppStateEntity(settingsJson = gson.toJson(j["settings"] ?: JsonObject()), syncedAt = System.currentTimeMillis()))
    }

    suspend fun saveAppointment(
        existingId: String?,
        client: ClientEntity,
        service: ServiceEntity,
        startsAt: String,
        price: Double = service.price,
        duration: Int = service.duration,
        comment: String = ""
    ): String {
        val localId = existingId ?: "local-${UUID.randomUUID()}"
        val end = Instant.parse(startsAt).plusSeconds((duration + 30L) * 60L).toString()
        val serviceJson = gson.toJson(listOf(mapOf(
            "service_id" to service.id,
            "name" to service.name,
            "standard_price" to service.price,
            "price" to price,
            "duration" to duration
        )))
        val oldStatus = existingId?.let { db.appointments().get(it)?.status } ?: "new"
        db.appointments().upsert(
            AppointmentEntity(
                id = localId,
                clientId = client.id,
                clientName = client.name,
                startsAt = startsAt,
                endsAt = end,
                status = oldStatus,
                servicesJson = serviceJson,
                paymentsJson = existingId?.let { db.appointments().get(it)?.paymentsJson } ?: "[]",
                comment = comment,
                pending = true
            )
        )
        val payload = linkedMapOf<String, Any?>(
            "id" to existingId,
            "client_id" to client.id,
            "starts_at" to startsAt,
            "services" to listOf(mapOf(
                "service_id" to service.id,
                "name" to service.name,
                "standard_price" to service.price,
                "price" to price,
                "duration" to duration
            )),
            "comment" to comment,
            "status" to oldStatus,
            "_local_id" to localId,
            "_client_name" to client.name
        )
        db.mutations().add(PendingMutationEntity(action = "save_appointment", payloadJson = gson.toJson(payload)))
        return localId
    }

    suspend fun cancel(id: String) = setStatus(id, "cancelled")
    suspend fun markUnpaid(id: String) = setStatus(id, "completed_unpaid")

    suspend fun setStatus(id: String, status: String) {
        db.appointments().setStatus(id, status)
        db.mutations().add(PendingMutationEntity(action = "set_status", payloadJson = gson.toJson(mapOf("id" to id, "status" to status))))
    }

    suspend fun payment(id: String, cash: Double, card: Double, other: Double) {
        val payments = gson.toJson(listOf(mapOf("total" to cash + card + other, "cash" to cash, "card" to card, "other" to other)))
        db.appointments().setPayment(id, payments)
        db.mutations().add(PendingMutationEntity(action = "payment", payloadJson = gson.toJson(mapOf("id" to id, "cash" to cash, "card" to card, "other" to other))))
    }

    suspend fun createClient(pin: String, name: String, phone: String, messenger: String): ClientEntity {
        val j = api.call(ApiEnvelope(pin, "create_client", mapOf("name" to name, "phone" to phone, "messenger" to messenger)))
        val c = ClientEntity(
            id = j["id"].asString,
            name = j["display_name"]?.asString ?: j["name"]?.asString ?: name,
            phone = j["phone"]?.asString ?: phone,
            messenger = j["messenger"]?.takeUnless { it.isJsonNull }?.asString ?: messenger
        )
        db.clients().upsert(c)
        return c
    }

    suspend fun flush(pin: String) {
        for (m in db.mutations().all()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(m.payloadJson, Map::class.java) as MutableMap<String, Any?>
                val localId = raw.remove("_local_id")?.toString()
                val clientName = raw.remove("_client_name")?.toString().orEmpty()
                val result = api.call(ApiEnvelope(pin, m.action, raw))
                if (m.action == "save_appointment") {
                    val serverId = result["id"]?.asString
                    if (localId != null && serverId != null && localId.startsWith("local-")) {
                        val old = db.appointments().get(localId)
                        if (old != null) {
                            db.appointments().delete(localId)
                            db.appointments().upsert(old.copy(id = serverId, clientName = clientName.ifBlank { old.clientName }, endsAt = result["ends_at"]?.asString ?: old.endsAt, pending = false))
                        }
                    } else if (localId != null) db.appointments().clearPending(localId)
                } else {
                    raw["id"]?.toString()?.let { db.appointments().clearPending(it) }
                }
                db.mutations().delete(m.localId)
            } catch (_: Exception) {
                db.mutations().fail(m.localId)
                break
            }
        }
    }
}
