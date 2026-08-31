package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.slotelly.app.data.AppointmentEntity
import ru.slotelly.app.data.AvailabilityOverrideEntity
import ru.slotelly.app.data.SlotellyRepository
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val availabilityZone = ZoneId.of("Europe/Moscow")
private val availabilityRu = Locale("ru", "RU")
private val fullWindowTimes = listOf("10:00", "13:00", "16:00", "19:00")
private const val bookingLink = "https://tblednyh1226-design.github.io/resnitsy-app/booking.html"

@Composable
fun AvailabilityOverlay(
    initialDate: LocalDate = LocalDate.now(availabilityZone),
    overrides: List<AvailabilityOverrideEntity>,
    appointments: List<AppointmentEntity>,
    repo: SlotellyRepository,
    pin: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var copied by remember { mutableStateOf(false) }

    val slots = fullWindowTimes.map { tm ->
        val start = LocalDateTime.parse("${date}T${tm}").atZone(availabilityZone).toInstant()
        val end = start.plus(Duration.ofHours(3))
        val iso = start.toString()
        val override = overrides.lastOrNull { sameMinute(it.slotStart, iso) }
        val occupied = appointments.any { a ->
            a.status != "cancelled" && runCatching {
                val aStart = Instant.parse(a.startsAt)
                val aEnd = Instant.parse(a.endsAt)
                aStart < end && aEnd > start
            }.getOrDefault(false)
        }
        val past = start <= Instant.now()
        val available = !past && !occupied && override?.available != false
        WindowState(tm, iso, available, occupied, past, override)
    }
    val availableTimes = slots.filter { it.available }.map { it.time }
    val shareText = buildWindowsText(date, availableTimes)

    FullOverlay(onClose) {
        Text("Окошки", style = MaterialTheme.typography.headlineSmall)
        Text("Полные 3-часовые окна для записи", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { date = date.minusDays(1); copied=false }) { Text("‹") }
            Text(
                date.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", availabilityRu)).replace(".", ""),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { date = LocalDate.now(availabilityZone); copied=false }) { Text("Сегодня") }
            FilledTonalIconButton(onClick = { date = date.plusDays(1); copied=false }) { Text("›") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(slots, key = { it.time }) { slot ->
                val container = when {
                    slot.occupied -> MaterialTheme.colorScheme.surfaceVariant
                    slot.available -> MaterialTheme.colorScheme.primaryContainer.copy(alpha=.72f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)
                }
                Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(slot.time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    slot.past -> "Время прошло"
                                    slot.occupied -> "Занято записью"
                                    slot.available -> if(slot.override?.available == true) "Свободно · открыто вручную" else "Свободно"
                                    else -> "Закрыто вручную"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (slot.override?.pending == true) Text("Сохраняется на сервер…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!slot.past && !slot.occupied) {
                            if (slot.available) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repo.setAvailability(slot.iso, false)
                                        launch { runCatching { repo.flush(pin) } }
                                    }
                                }) { Text("Закрыть") }
                            } else {
                                Button(onClick = {
                                    scope.launch {
                                        repo.setAvailability(slot.iso, true)
                                        launch { runCatching { repo.flush(pin) } }
                                    }
                                }) { Text("Открыть") }
                            }
                        }
                    }
                }
            }
        }

        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Список для клиента",fontWeight=FontWeight.Bold)
                Text(shareText,style=MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(
                        onClick={
                            val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Свободные окошки",shareText))
                            copied=true
                        },
                        enabled=availableTimes.isNotEmpty(),
                        modifier=Modifier.weight(1f)
                    ){Text(if(copied)"Скопировано ✓" else "Копировать")}
                    OutlinedButton(
                        onClick={
                            val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,shareText)}
                            context.startActivity(Intent.createChooser(intent,"Отправить окошки"))
                        },
                        enabled=availableTimes.isNotEmpty(),
                        modifier=Modifier.weight(1f)
                    ){Text("Поделиться")}
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Изменения сохраняются на телефоне сразу и синхронизируются автоматически.", style = MaterialTheme.typography.bodySmall)
    }
}

private data class WindowState(
    val time:String,
    val iso:String,
    val available:Boolean,
    val occupied:Boolean,
    val past:Boolean,
    val override:AvailabilityOverrideEntity?
)

private fun buildWindowsText(date:LocalDate,times:List<String>):String{
    val prettyDate=date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", availabilityRu))
    return if(times.isEmpty()) {
        "Свободных окошек на $prettyDate пока нет"
    } else {
        buildString {
            append("Свободные окошки ✨\n")
            append(prettyDate.replaceFirstChar { if(it.isLowerCase()) it.titlecase(availabilityRu) else it.toString() })
            append("\n\n")
            times.forEach { append("• $it\n") }
            append("\nЗаписаться онлайн: $bookingLink")
        }
    }
}

private fun sameMinute(a: String, b: String): Boolean = runCatching {
    kotlin.math.abs(Instant.parse(a).epochSecond - Instant.parse(b).epochSecond) < 60
}.getOrDefault(false)
