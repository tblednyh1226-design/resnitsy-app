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
import ru.slotelly.app.data.ServiceEntity
import java.time.*
import java.time.format.DateTimeFormatter

private val editorZone = ZoneId.of("Europe/Moscow")

private fun digits(x: String) = x.filter(Char::isDigit).let { if (it.length == 11 && it.startsWith("8")) "7" + it.drop(1) else it }
private fun localDateValue(iso: String): String = Instant.parse(iso).atZone(editorZone).toLocalDate().toString()
private fun localTimeValue(iso: String): String = Instant.parse(iso).atZone(editorZone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
private fun firstService(existing: AppointmentEntity?): Pair<String?, Triple<Double, Int, String>?> {
    if (existing == null) return null to null
    return runCatching {
        val a = JsonParser.parseString(existing.servicesJson).asJsonArray
        if (a.size() == 0) return@runCatching null to null
        val o = a[0].asJsonObject
        val id = o["service_id"]?.takeUnless { it.isJsonNull }?.asString
        id to Triple(o["price"]?.asDouble ?: 0.0, o["duration"]?.asInt ?: 60, o["name"]?.asString ?: "")
    }.getOrDefault(null to null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastAppointmentEditor(
    existing: AppointmentEntity?,
    clients: List<ClientEntity>,
    services: List<ServiceEntity>,
    onClose: () -> Unit,
    onSave: (ClientEntity, ServiceEntity, String, Double, Int, String) -> Unit
) {
    val old = remember(existing) { firstService(existing) }
    var client by remember(existing, clients) { mutableStateOf(existing?.clientId?.let { id -> clients.find { it.id == id } }) }
    var query by remember(existing) { mutableStateOf(existing?.clientName.orEmpty()) }
    var service by remember(existing, services) { mutableStateOf(services.find { it.id == old.first }) }
    var serviceMenu by remember { mutableStateOf(false) }
    var date by remember(existing) { mutableStateOf(existing?.startsAt?.let(::localDateValue) ?: LocalDate.now(editorZone).toString()) }
    var time by remember(existing) { mutableStateOf(existing?.startsAt?.let(::localTimeValue) ?: "10:00") }
    var price by remember(existing) { mutableStateOf((old.second?.first ?: service?.price ?: 0.0).toInt().toString()) }
    var duration by remember(existing) { mutableStateOf((old.second?.second ?: service?.duration ?: 60).toString()) }
    var comment by remember(existing) { mutableStateOf(existing?.comment.orEmpty()) }
    var error by remember { mutableStateOf("") }

    val q = query.trim().lowercase()
    val qDigits = digits(query)
    val matches = remember(q, qDigits, clients) {
        if (q.length < 2 && qDigits.length < 3) emptyList()
        else clients.filter { c ->
            c.name.lowercase().contains(q) || (qDigits.isNotBlank() && digits(c.phone).contains(qDigits))
        }.take(12)
    }

    FullOverlay(onClose) {
        Text(if (existing == null) "Новая запись" else "Редактирование", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                if (client != null && value != client?.name) client = null
            },
            label = { Text("Клиент") },
            placeholder = { Text("Имя или телефон") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (client == null && matches.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().heightIn(max = 190.dp)) {
                LazyColumn {
                    items(matches, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                client = c
                                query = c.name
                                error = ""
                            }.padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Column {
                                Text(c.name, fontWeight = FontWeight.SemiBold)
                                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = serviceMenu, onExpandedChange = { serviceMenu = !serviceMenu }) {
            OutlinedTextField(
                value = service?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Услуга") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(serviceMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = serviceMenu, onDismissRequest = { serviceMenu = false }) {
                services.forEach { s ->
                    DropdownMenuItem(
                        text = { Column { Text(s.name); Text("${s.price.toInt()} ₽ · ${s.duration} мин", style = MaterialTheme.typography.bodySmall) } },
                        onClick = {
                            service = s
                            price = s.price.toInt().toString()
                            duration = s.duration.toString()
                            serviceMenu = false
                            error = ""
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Дата") }, singleLine = true, modifier = Modifier.weight(1.25f))
            OutlinedTextField(time, { time = it }, label = { Text("Время") }, singleLine = true, modifier = Modifier.weight(.75f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() } }, label = { Text("Цена, ₽") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(duration, { duration = it.filter { ch -> ch.isDigit() } }, label = { Text("Минут") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp))
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                val c = client ?: matches.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
                val s = service
                if (c == null) { error = "Выберите клиента"; return@Button }
                if (s == null) { error = "Выберите услугу"; return@Button }
                val start = runCatching { ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), editorZone).toInstant().toString() }.getOrNull()
                if (start == null) { error = "Проверьте дату и время"; return@Button }
                val p = price.toDoubleOrNull() ?: 0.0
                val d = duration.toIntOrNull() ?: s.duration
                if (d <= 0) { error = "Проверьте длительность"; return@Button }
                onSave(c, s, start, p, d, comment)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить") }
    }
}
