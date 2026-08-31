package ru.slotelly.app.data

import com.google.gson.JsonObject
import java.time.Instant

class SlotellyExtras(private val api: SlotellyApi = SlotellyApi.create()) {
    suspend fun waitlist(pin: String): List<WaitlistItem> {
        val j = api.waitlist(mapOf("p_pin" to pin))
        return j.getAsJsonArray("rows")?.map { e ->
            val o = e.asJsonObject
            WaitlistItem(
                id = o["id"]?.asString.orEmpty(),
                clientName = o["client_name"]?.takeUnless { it.isJsonNull }?.asString ?: "Клиент",
                phone = o["phone"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                desiredText = o["desired_text"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                status = o["status"]?.asString ?: "active",
                requestType = o["request_type"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                appointmentStart = o["appointment_start"]?.takeUnless { it.isJsonNull }?.asString
            )
        } ?: emptyList()
    }

    suspend fun report(pin: String, from: Instant, to: Instant): ReportSummary {
        val j: JsonObject = api.call(ApiEnvelope(pin, "report", mapOf("from" to from.toString(), "to" to to.toString())))
        var total = 0.0
        var cash = 0.0
        var card = 0.0
        var other = 0.0
        var paid = 0
        var unpaid = 0
        val rows = j.getAsJsonArray("rows") ?: return ReportSummary()
        rows.forEach { e ->
            val o = e.asJsonObject
            if (o["status"]?.asString == "completed_unpaid") unpaid++
            val payments = o.getAsJsonArray("payments")
            if (payments != null && payments.size() > 0) {
                paid++
                payments.forEach { p ->
                    val x = p.asJsonObject
                    total += x["total_amount"]?.asDouble ?: 0.0
                    cash += x["cash_amount"]?.asDouble ?: 0.0
                    card += x["card_amount"]?.asDouble ?: 0.0
                    other += x["other_amount"]?.asDouble ?: 0.0
                }
            }
        }
        return ReportSummary(total, cash, card, other, paid, unpaid)
    }
}

data class WaitlistItem(
    val id: String,
    val clientName: String,
    val phone: String,
    val desiredText: String,
    val status: String,
    val requestType: String,
    val appointmentStart: String?
)

data class ReportSummary(
    val total: Double = 0.0,
    val cash: Double = 0.0,
    val card: Double = 0.0,
    val other: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidCount: Int = 0
)
