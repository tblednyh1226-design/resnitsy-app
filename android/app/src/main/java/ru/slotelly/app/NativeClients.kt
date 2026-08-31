package ru.slotelly.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import ru.slotelly.app.data.AppointmentEntity
import ru.slotelly.app.data.ClientEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLIENT_ZONE = ZoneId.of("Europe/Moscow")
private val CLIENT_RU = Locale("ru", "RU")
private fun clientDigits(s: String) = s.filter(Char::isDigit)
private fun clientServiceNames(json: String): String = runCatching {
    JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
        val o=e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")
private fun clientDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(CLIENT_ZONE).format(DateTimeFormatter.ofPattern("dd.MM (EEE) HH:mm", CLIENT_RU))
}.getOrDefault(iso)
private fun clientTotal(json: String): Int = runCatching {
    JsonParser.parseString(json).asJsonArray.sumOf { e ->
        val o=e.asJsonObject
        o["price"]?.asDouble ?: o["actual_price"]?.asDouble ?: 0.0
    }.toInt()
}.getOrDefault(0)

@Composable
fun NativeClientsScreen(
    clients: List<ClientEntity>,
    appointments: List<AppointmentEntity>,
    onNew: () -> Unit,
    onOpenAppointment: (AppointmentEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ClientEntity?>(null) }
    val qDigits=clientDigits(query)
    val rows=remember(clients,query) {
        val q=query.trim()
        if(q.isBlank()) clients else clients.filter { c ->
            c.name.contains(q,true) || (qDigits.isNotBlank() && clientDigits(c.phone).contains(qDigits))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)) {
        Row {
            OutlinedTextField(query,{query=it},label={Text("Имя или телефон")},singleLine=true,modifier=Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick=onNew){Text("+")}
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            items(rows,key={it.id}) { c ->
                Card(Modifier.fillMaxWidth().clickable{selected=c}) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name,fontWeight=FontWeight.SemiBold)
                        if(c.phone.isNotBlank()) Text(c.phone)
                        val last=clientServiceNames(c.lastServicesJson)
                        if(last.isNotBlank()) Text("Последнее: $last",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    selected?.let { c ->
        val history=appointments.filter{it.clientId==c.id}.sortedByDescending{it.startsAt}
        FullOverlay(onClose={selected=null}) {
            Text(c.name,style=MaterialTheme.typography.headlineSmall)
            if(c.phone.isNotBlank()) Text(c.phone,style=MaterialTheme.typography.titleMedium)
            if(c.messenger.isNotBlank()) Text("Мессенджер: ${c.messenger}",style=MaterialTheme.typography.bodyMedium)
            val last=clientServiceNames(c.lastServicesJson)
            if(last.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Последняя услуга: $last") }
            Spacer(Modifier.height(14.dp))
            Text("Записи",style=MaterialTheme.typography.titleMedium)
            if(history.isEmpty()) {
                Text("В локальном календаре записей нет",style=MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=6.dp)) {
                    items(history,key={it.id}) { a ->
                        Card(Modifier.fillMaxWidth().clickable{selected=null;onOpenAppointment(a)}) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(clientDate(a.startsAt),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                    val total=clientTotal(a.servicesJson)
                                    if(total>0) Text("$total ₽")
                                }
                                Text(clientServiceNames(a.servicesJson))
                                Text(when(a.status){"completed_paid"->"Оплачено";"completed_unpaid"->"Без оплаты";"cancelled"->"Отменено";else->"Запись"},style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
