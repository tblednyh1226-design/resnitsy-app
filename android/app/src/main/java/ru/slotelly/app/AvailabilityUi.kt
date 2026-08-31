package ru.slotelly.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.slotelly.app.data.AvailabilityOverrideEntity
import ru.slotelly.app.data.SlotellyRepository
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val availabilityZone = ZoneId.of("Europe/Moscow")
private val availabilityRu = Locale("ru", "RU")

@Composable
fun AvailabilityOverlay(
    initialDate: LocalDate = LocalDate.now(availabilityZone),
    overrides: List<AvailabilityOverrideEntity>,
    repo: SlotellyRepository,
    pin: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var type by remember { mutableStateOf("Обычные") }
    val times = when (type) {
        "Короткие" -> listOf("11:00", "13:00", "14:00", "16:00", "17:00", "19:00")
        "Брови" -> listOf("11:30", "13:00", "14:30", "16:00", "17:30", "19:00")
        else -> listOf("10:00", "13:00", "16:00", "19:00")
    }

    FullOverlay(onClose) {
        Text("Окошки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { date = date.minusDays(1) }) { Text("‹") }
            Text(
                date.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", availabilityRu)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { date = LocalDate.now(availabilityZone) }) { Text("Сегодня") }
            TextButton(onClick = { date = date.plusDays(1) }) { Text("›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Обычные", "Короткие", "Брови").forEach { label ->
                FilterChip(selected = type == label, onClick = { type = label }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Ручное состояние имеет приоритет над шаблоном расписания.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(times, key = { it }) { tm ->
                val instant = LocalDateTime.parse("${date}T${tm}").atZone(availabilityZone).toInstant()
                val iso = instant.toString()
                val override = overrides.lastOrNull { sameMinute(it.slotStart, iso) }
                val isPast = instant <= Instant.now()
                val stateText = when {
                    isPast -> "Прошло"
                    override == null -> "По расписанию"
                    override.available -> "Открыто вручную"
                    else -> "Закрыто вручную"
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tm, style = MaterialTheme.typography.titleMedium)
                            Text(stateText, style = MaterialTheme.typography.bodySmall)
                            if (override?.pending == true) Text("Ждёт синхронизации", style = MaterialTheme.typography.labelSmall)
                        }
                        if (!isPast) {
                            if (override?.available == false) {
                                Button(onClick = {
                                    scope.launch {
                                        repo.setAvailability(iso, true)
                                        launch { runCatching { repo.flush(pin) } }
                                    }
                                }) { Text("Открыть") }
                            } else {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repo.setAvailability(iso, false)
                                        launch { runCatching { repo.flush(pin) } }
                                    }
                                }) { Text("Закрыть") }
                            }
                        }
                    }
                }
            }
        }
        Text("Изменения сохраняются на телефоне сразу. При появлении сети Slotelly отправит их на сервер автоматически.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun sameMinute(a: String, b: String): Boolean = runCatching {
    kotlin.math.abs(Instant.parse(a).epochSecond - Instant.parse(b).epochSecond) < 60
}.getOrDefault(false)
