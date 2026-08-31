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
import ru.slotelly.app.data.ServiceSelection
import java.time.*
import java.time.format.DateTimeFormatter

private val MULTI_ZONE = ZoneId.of("Europe/Moscow")
private fun multiDigits(x: String) = x.filter(Char::isDigit).let { if (it.length == 11 && it.startsWith("8")) "7" + it.drop(1) else it }
private fun multiDate(iso: String) = Instant.parse(iso).atZone(MULTI_ZONE).toLocalDate().toString()
private fun multiTime(iso: String) = Instant.parse(iso).atZone(MULTI_ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun parseExistingSelections(existing: AppointmentEntity?, services: List<ServiceEntity>): List<ServiceSelection> {
    if (existing == null) return emptyList()
    return runCatching {
        JsonParser.parseString(existing.servicesJson).asJsonArray.mapNotNull { e ->
            val o = e.asJsonObject
            val id = o["service_id"]?.takeUnless { it.isJsonNull }?.asString
            val name = o["name"]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val live = services.firstOrNull { it.id == id } ?: services.firstOrNull { it.name.equals(name, true) }
            live?.let {
                ServiceSelection(
                    service = it,
                    price = o["price"]?.asDouble ?: o["standard_price"]?.asDouble ?: it.price,
                    duration = o["duration"]?.asInt ?: it.duration
                )
            }
        }
    }.getOrDefault(emptyList())
}

private data class ServiceRowState(
    val key: Long,
    val service: ServiceEntity? = null,
    val price: String = "",
    val duration: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiServiceAppointmentEditor(
    existing: AppointmentEntity?,
    clients: List<ClientEntity>,
    services: List<ServiceEntity>,
    onClose: () -> Unit,
    onSave: (ClientEntity, List<ServiceSelection>, String, String) -> Unit
) {
    val oldSelections = remember(existing, services) { parseExistingSelections(existing, services) }
    var client by remember(existing, clients) { mutableStateOf(existing?.clientId?.let { id -> clients.find { it.id == id } }) }
    var query by remember(existing) { mutableStateOf(existing?.clientName.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.startsAt?.let(::multiDate) ?: LocalDate.now(MULTI_ZONE).toString()) }
    var time by remember(existing) { mutableStateOf(existing?.startsAt?.let(::multiTime) ?: "10:00") }
    var comment by remember(existing) { mutableStateOf(existing?.comment.orEmpty()) }
    var error by remember { mutableStateOf("") }
    var nextKey by remember { mutableLongStateOf(1000L) }
    var rows by remember(existing, oldSelections) {
        mutableStateOf(
            if (oldSelections.isNotEmpty()) oldSelections.mapIndexed { i, x ->
                ServiceRowState(i.toLong(), x.service, x.price.toInt().toString(), x.duration.toString())
            } else listOf(ServiceRowState(0L))
        )
    }

    val q = query.trim().lowercase()
    val qDigits = multiDigits(query)
    val matches = remember(q, qDigits, clients) {
        if (q.length < 2 && qDigits.length < 3) emptyList()
        else clients.filter { c ->
            c.name.lowercase().contains(q) || (qDigits.isNotBlank() && multiDigits(c.phone).contains(qDigits))
        }.take(12)
    }

    FullOverlay(onClose) {
        Text(if (existing == null) "Новая запись" else "Редактирование", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { value -> query = value; if (client != null && value != client?.name) client = null },
            label = { Text("Клиент") },
            placeholder = { Text("Имя или телефон") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (client == null && matches.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                LazyColumn {
                    items(matches, key = { it.id }) { c ->
                        Column(
                            Modifier.fillMaxWidth().clickable { client = c; query = c.name; error = "" }.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Дата") }, singleLine = true, modifier = Modifier.weight(1.25f))
            OutlinedTextField(time, { time = it }, label = { Text("Время") }, singleLine = true, modifier = Modifier.weight(.75f))
        }

        Spacer(Modifier.height(8.dp))
        Text("Услуги", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(rows, key = { it.key }) { row ->
                var menu by remember(row.key) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = !menu }) {
                            OutlinedTextField(
                                value = row.service?.name.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Услуга") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                services.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Column { Text(s.name); Text("${s.price.toInt()} ₽ · ${s.duration} мин", style = MaterialTheme.typography.bodySmall) } },
                                        onClick = {
                                            rows = rows.map { if (it.key == row.key) it.copy(service = s, price = s.price.toInt().toString(), duration = s.duration.toString()) else it }
                                            menu = false
                                            error = ""
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.price,
                                onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(price = v.filter(Char::isDigit)) else it } },
                                label = { Text("Цена, ₽") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.duration,
                                onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(duration = v.filter(Char::isDigit)) else it } },
                                label = { Text("Минут") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rows.size > 1) TextButton(onClick = { rows = rows.filterNot { it.key == row.key } }) { Text("Удалить услугу") }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { nextKey += 1; rows = rows + ServiceRowState(nextKey) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Добавить услугу") }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                val c = client ?: matches.firstOrNull { it.name.equals(query.trim(), true) }
                if (c == null) { error = "Выберите клиента"; return@Button }
                if (rows.any { it.service == null }) { error = "Выберите все услуги"; return@Button }
                val selected = rows.mapNotNull { r ->
                    r.service?.let { s -> ServiceSelection(s, r.price.toDoubleOrNull() ?: s.price, r.duration.toIntOrNull() ?: s.duration) }
                }
                if (selected.isEmpty() || selected.any { it.duration <= 0 }) { error = "Проверьте услуги и длительность"; return@Button }
                val start = runCatching { ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), MULTI_ZONE).toInstant().toString() }.getOrNull()
                if (start == null) { error = "Проверьте дату и время"; return@Button }
                onSave(c, selected, start, comment)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить") }
    }
}
