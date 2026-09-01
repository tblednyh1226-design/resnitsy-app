package ru.slotelly.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.time.Instant

private const val SLOTELLY_WORKSPACE = "11111111-1111-4111-8111-111111111111"
private val extrasGson = Gson()
private fun jsonBody(values: Map<String, Any?>): JsonObject = extrasGson.toJsonTree(values).asJsonObject

class SlotellyExtras(private val api: SlotellyApi = SlotellyApi.create()) {
    suspend fun waitlist(pin: String): List<WaitlistItem> = parseWaitlist(api.waitlist(mapOf("p_pin" to pin)))
    suspend fun waitlistAll(pin: String): List<WaitlistItem> = parseWaitlist(api.waitlistAll(mapOf("p_pin" to pin)))

    private fun parseWaitlist(j: JsonObject): List<WaitlistItem> = j.getAsJsonArray("rows")?.map { e ->
        val o = e.asJsonObject
        WaitlistItem(
            id = o["id"]?.asString.orEmpty(), clientId = o["client_id"]?.takeUnless { it.isJsonNull }?.asString,
            clientName = o["client_name"]?.takeUnless { it.isJsonNull }?.asString ?: "Клиент",
            phone = o["phone"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(), desiredText = o["desired_text"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            status = o["status"]?.asString ?: "active", requestType = o["request_type"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            appointmentStart = o["appointment_start"]?.takeUnless { it.isJsonNull }?.asString, appointmentEnd = o["appointment_end"]?.takeUnless { it.isJsonNull }?.asString,
            dateFrom = o["date_from"]?.takeUnless { it.isJsonNull }?.asString, dateTo = o["date_to"]?.takeUnless { it.isJsonNull }?.asString,
            timeFrom = o["time_from"]?.takeUnless { it.isJsonNull }?.asString, timeTo = o["time_to"]?.takeUnless { it.isJsonNull }?.asString,
            preferredMessenger = o["preferred_messenger"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(), createdAt = o["created_at"]?.takeUnless { it.isJsonNull }?.asString,
            updatedAt = o["updated_at"]?.takeUnless { it.isJsonNull }?.asString
        )
    } ?: emptyList()

    private fun parseDetail(j: JsonObject, fallbackId: String = ""): WaitlistDetail {
        val requestObj = j.getAsJsonObject("request") ?: JsonObject()
        val request = parseWaitlist(JsonObject().apply { add("rows", com.google.gson.JsonArray().apply { add(requestObj) }) }).firstOrNull() ?: WaitlistItem(id=fallbackId, clientName="Клиент")
        val history = j.getAsJsonArray("history")?.map { e -> val o=e.asJsonObject; WaitlistHistoryItem(o["id"]?.asString.orEmpty(),o["event_type"]?.asString.orEmpty(),o["event_text"]?.takeUnless{it.isJsonNull}?.asString.orEmpty(),o["offered_at"]?.takeUnless{it.isJsonNull}?.asString,o["created_at"]?.takeUnless{it.isJsonNull}?.asString.orEmpty()) } ?: emptyList()
        val offers = j.getAsJsonArray("offers")?.map { e -> val o=e.asJsonObject; WaitlistOfferItem(o["id"]?.asString.orEmpty(),o["offered_start"]?.asString.orEmpty(),o["offered_end"]?.asString.orEmpty(),o["channel"]?.takeUnless{it.isJsonNull}?.asString.orEmpty(),o["status"]?.asString.orEmpty(),o["sent_at"]?.takeUnless{it.isJsonNull}?.asString,o["responded_at"]?.takeUnless{it.isJsonNull}?.asString) } ?: emptyList()
        return WaitlistDetail(request,history,offers)
    }

    suspend fun waitlistDetail(pin: String, id: String): WaitlistDetail = parseDetail(api.waitlistDetail(jsonBody(mapOf("p_pin" to pin,"p_id" to id))),id)

    suspend fun updateWaitlist(pin:String,item:WaitlistItem,desiredText:String,dateFrom:String?,dateTo:String?,timeFrom:String?,timeTo:String?,preferredMessenger:String,status:String?=null): WaitlistDetail {
        val body=mutableMapOf<String,Any?>("p_pin" to pin,"p_id" to item.id,"p_desired_text" to desiredText,"p_preferred_messenger" to preferredMessenger)
        dateFrom?.let{body["p_date_from"]=it};dateTo?.let{body["p_date_to"]=it};timeFrom?.let{body["p_time_from"]=it};timeTo?.let{body["p_time_to"]=it};status?.let{body["p_status"]=it}
        return parseDetail(api.waitlistUpdate(jsonBody(body)),item.id)
    }

    suspend fun clientExtra(pin:String,clientId:String):ClientExtra{
        val j=api.clientExtra(jsonBody(mapOf("p_pin" to pin,"p_client" to clientId)));val tg=j.getAsJsonObject("telegram")
        return ClientExtra(j["birth_date"]?.takeUnless{it.isJsonNull}?.asString,j["comment"]?.takeUnless{it.isJsonNull}?.asString.orEmpty(),j["discount_percent"]?.takeUnless{it.isJsonNull}?.asDouble?:0.0,tg?.get("linked")?.asBoolean?:false,tg?.get("username")?.takeUnless{it.isJsonNull}?.asString.orEmpty())
    }

    suspend fun updateClient(pin:String,clientId:String,name:String,phone:String,messenger:String,birthDate:String?):ClientExtra{
        val body=mutableMapOf<String,Any?>("p_pin" to pin,"p_client" to clientId,"p_name" to name,"p_phone" to phone,"p_messenger" to messenger);birthDate?.let{body["p_birth_date"]=it};api.updateClient(jsonBody(body));return clientExtra(pin,clientId)
    }

    suspend fun telegramStatus(pin:String,clientId:String):ClientExtra{
        val j=api.telegramLinkStatus(jsonBody(mapOf("p_workspace" to SLOTELLY_WORKSPACE,"p_pin" to pin,"p_client" to clientId)));return ClientExtra(telegramLinked=j["linked"]?.asBoolean?:false,telegramUsername=j["username"]?.takeUnless{it.isJsonNull}?.asString.orEmpty())
    }
    suspend fun createTelegramLink(pin:String,clientId:String):String=api.createTelegramLink(jsonBody(mapOf("p_workspace" to SLOTELLY_WORKSPACE,"p_pin" to pin,"p_client" to clientId)))["url"]?.takeUnless{it.isJsonNull}?.asString.orEmpty()

    suspend fun report(pin:String,from:Instant,to:Instant):ReportSummary{
        val j=api.call(ApiEnvelope(pin,"report",mapOf("from" to from.toString(),"to" to to.toString())));var total=0.0;var cash=0.0;var card=0.0;var other=0.0;var paid=0;var unpaid=0;var plan=0.0
        val rows=j.getAsJsonArray("rows")?:return ReportSummary();rows.forEach{e->val o=e.asJsonObject;if(o["status"]?.asString=="completed_unpaid")unpaid++;if(o["status"]?.asString!="cancelled")o.getAsJsonArray("services")?.forEach{s->plan+=s.asJsonObject["price"]?.asDouble?:0.0};val payments=o.getAsJsonArray("payments");if(payments!=null&&payments.size()>0){paid++;payments.forEach{p->val x=p.asJsonObject;total+=x["total_amount"]?.asDouble?:0.0;cash+=x["cash_amount"]?.asDouble?:0.0;card+=x["card_amount"]?.asDouble?:0.0;other+=x["other_amount"]?.asDouble?:0.0}}}
        return ReportSummary(total,cash,card,other,paid,unpaid,plan)
    }
}

data class ClientExtra(val birthDate:String?=null,val comment:String="",val discountPercent:Double=0.0,val telegramLinked:Boolean=false,val telegramUsername:String="")
data class WaitlistItem(val id:String,val clientId:String?=null,val clientName:String,val phone:String="",val desiredText:String="",val status:String="active",val requestType:String="",val appointmentStart:String?=null,val appointmentEnd:String?=null,val dateFrom:String?=null,val dateTo:String?=null,val timeFrom:String?=null,val timeTo:String?=null,val preferredMessenger:String="",val createdAt:String?=null,val updatedAt:String?=null)
data class WaitlistHistoryItem(val id:String,val eventType:String,val eventText:String,val offeredAt:String?,val createdAt:String)
data class WaitlistOfferItem(val id:String,val offeredStart:String,val offeredEnd:String,val channel:String,val status:String,val sentAt:String?,val respondedAt:String?)
data class WaitlistDetail(val request:WaitlistItem,val history:List<WaitlistHistoryItem>,val offers:List<WaitlistOfferItem>)
data class ReportSummary(val total:Double=0.0,val cash:Double=0.0,val card:Double=0.0,val other:Double=0.0,val paidCount:Int=0,val unpaidCount:Int=0,val plan:Double=0.0)
