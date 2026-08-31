package ru.slotelly.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import ru.slotelly.app.data.NativeSettingsExtras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ST_RU = Locale("ru", "RU")

@Composable
fun ScheduleToolsSection(pin: String, api: NativeSettingsExtras) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var dayoffs by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var breaks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var workdayOverrides by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var breakStart by remember { mutableStateOf("14:00") }
    var breakEnd by remember { mutableStateOf("14:30") }
    var breakLabel by remember { mutableStateOf("Перерыв") }
    var showBreakForm by remember { mutableStateOf(false) }
    var needsForce by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = ""
        val d = date.toString()
        runCatching {
            val w = api.weekly(pin)
            workdayOverrides = w.getAsJsonArray("workday_overrides")?.map { it.asString }?.toSet() ?: emptySet()
            dayoffs = api.dayoffs(pin, d, d).getAsJsonArray("rows")?.map { it.asJsonObject } ?: emptyList()
            breaks = api.breaks(pin, d, d).getAsJsonArray("rows")?.map { it.asJsonObject } ?: emptyList()
        }.onFailure { error = it.message ?: "Не удалось загрузить расписание" }
        loading = false
    }

    LaunchedEffect(date) { load() }
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text("Исключения и перерывы", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { date = date.minusDays(1) }) { Text("‹") }
        Text(date.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", ST_RU)).replace(".", ""), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { date = LocalDate.now() }) { Text("Сегодня") }
        TextButton(onClick = { date = date.plusDays(1) }) { Text("›") }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

    val d = date.toString()
    val isDayoff = dayoffs.isNotEmpty()
    val isWorkdayOverride = d in workdayOverrides
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("День", fontWeight = FontWeight.SemiBold)
            Text(when {
                isDayoff -> "Выходной"
                isWorkdayOverride -> "Рабочий день-исключение"
                else -> "По обычному расписанию"
            }, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            if (isDayoff) {
                OutlinedButton(onClick = { scope.launch { runCatching { api.unsetDayoff(pin, d) }.onSuccess { load() }.onFailure { error = it.message ?: "Ошибка" } } }, modifier = Modifier.fillMaxWidth()) { Text("Убрать выходной") }
            } else {
                OutlinedButton(onClick = { scope.launch {
                    runCatching { api.setDayoff(pin, d, false) }
                        .onSuccess { result ->
                            needsForce = result.get("needs_confirm")?.asBoolean == true
                            if (!needsForce) load()
                        }
                        .onFailure { error = it.message ?: "Не удалось поставить выходной" }
                } }, modifier = Modifier.fillMaxWidth()) { Text("Сделать выходным") }
            }
            if (isWorkdayOverride) {
                TextButton(onClick = { scope.launch { runCatching { api.unsetWorkdayOverride(pin, d) }.onSuccess { load() } } }, modifier = Modifier.fillMaxWidth()) { Text("Убрать рабочее исключение") }
            } else if (!isDayoff) {
                TextButton(onClick = { scope.launch { runCatching { api.setWorkdayOverride(pin, d) }.onSuccess { load() }.onFailure { error = it.message ?: "Ошибка" } } }, modifier = Modifier.fillMaxWidth()) { Text("Сделать рабочим исключением") }
            }
        }
    }
    if (needsForce) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("На этот день уже есть записи", fontWeight = FontWeight.SemiBold)
                Text("Выходной не удалит записи, но закроет день для новых окон.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { scope.launch { runCatching { api.setDayoff(pin, d, true) }.onSuccess { needsForce = false; load() }.onFailure { error = it.message ?: "Ошибка" } } }, modifier = Modifier.fillMaxWidth()) { Text("Всё равно сделать выходным") }
                TextButton(onClick = { needsForce = false }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Перерывы", fontWeight = FontWeight.SemiBold)
    if (breaks.isEmpty()) Text("На этот день перерывов нет", style = MaterialTheme.typography.bodySmall)
    breaks.forEach { b ->
        val id = b.get("id")?.asString.orEmpty()
        val starts = b.get("starts_at")?.asString.orEmpty()
        val ends = b.get("ends_at")?.asString.orEmpty()
        val label = b.get("label")?.asString ?: "Перерыв"
        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text("${shortTime(starts)}–${shortTime(ends)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { scope.launch { runCatching { api.deleteBreak(pin, id) }.onSuccess { load() }.onFailure { error = it.message ?: "Ошибка" } } }) { Text("Удалить") }
            }
        }
    }
    if (!showBreakForm) {
        OutlinedButton(onClick = { showBreakForm = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить перерыв") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(breakStart, { breakStart = it }, label = { Text("С") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(breakEnd, { breakEnd = it }, label = { Text("До") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedTextField(breakLabel, { breakLabel = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { scope.launch {
            runCatching { api.createBreak(pin, d, breakStart, breakEnd, breakLabel) }
                .onSuccess { showBreakForm = false; load() }
                .onFailure { error = it.message ?: "Не удалось сохранить перерыв" }
        } }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить перерыв") }
        TextButton(onClick = { showBreakForm = false }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}

private fun shortTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(iso.take(5))
