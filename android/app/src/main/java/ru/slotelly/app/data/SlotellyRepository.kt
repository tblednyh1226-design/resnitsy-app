package ru.slotelly.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import java.time.*

class SlotellyRepository(private val db:SlotellyDatabase,private val api:SlotellyApi=SlotellyApi.create()) {
    private val gson=Gson()
    fun appointments():Flow<List<AppointmentEntity>> = db.appointments().observeActive()
    fun clients():Flow<List<ClientEntity>> = db.clients().observeAll()
    fun services():Flow<List<ServiceEntity>> = db.services().observeActive()

    suspend fun sync(pin:String){
        val now=ZonedDateTime.now(ZoneId.of("Europe/Moscow")); val from=now.minusDays(21).toInstant().toString(); val to=now.plusDays(45).toInstant().toString()
        val j=api.call(ApiEnvelope(pin,"snapshot",mapOf("from" to from,"to" to to,"include_clients" to true)))
        val serviceRows=j.getAsJsonArray("services")?.map { x->val o=x.asJsonObject;ServiceEntity(o["id"].asString,o["name"].asString,o["base_price"]?.asDouble?:0.0,o["duration_minutes"]?.asInt?:60,o["is_active"]?.asBoolean?:true)}?:emptyList()
        db.services().upsert(serviceRows)
        val clientRows=j.getAsJsonArray("clients")?.map { x->val o=x.asJsonObject;ClientEntity(o["id"].asString,o["display_name"]?.asString?:"Клиент",o["phone"]?.asString?:"",o["messenger"]?.asString?:"") }?:emptyList()
        db.clients().upsert(clientRows)
        val arr=j.getAsJsonObject("calendar")?.getAsJsonArray("appointments")
        val rows=arr?.map { x->val o=x.asJsonObject;AppointmentEntity(o["id"].asString,o["client_id"]?.takeUnless{it.isJsonNull}?.asString,o["client_name"]?.asString?:"Клиент",o["starts_at"].asString,o["ends_at"].asString,o["status"]?.asString?:"new",gson.toJson(o["services"]),gson.toJson(o["payments"]),false) }?:emptyList()
        db.appointments().upsert(rows)
        flush(pin)
    }

    suspend fun cancel(id:String){ db.appointments().setStatus(id,"cancelled"); db.mutations().add(PendingMutationEntity(action="set_status",payloadJson=gson.toJson(mapOf("id" to id,"status" to "cancelled")))) }
    suspend fun markUnpaid(id:String){ db.appointments().setStatus(id,"completed_unpaid"); db.mutations().add(PendingMutationEntity(action="set_status",payloadJson=gson.toJson(mapOf("id" to id,"status" to "completed_unpaid")))) }
    suspend fun flush(pin:String){
        for(m in db.mutations().all()) try { val payload=gson.fromJson(m.payloadJson,Map::class.java) as Map<String,Any?>; api.call(ApiEnvelope(pin,m.action,payload)); db.mutations().delete(m.localId) } catch(_:Exception){ db.mutations().fail(m.localId); break }
    }
}
