package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import ru.slotelly.app.data.AppointmentEntity
import ru.slotelly.app.data.AvailabilityOverrideEntity
import ru.slotelly.app.data.SlotellyRepository
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val availabilityZone = ZoneId.of("Europe/Moscow")
private val availabilityRu = Locale("ru", "RU")
private const val bookingLink = "https://tblednyh1226-design.github.io/resnitsy-app/booking.html"
private val allHalfHourTimes = buildList {
    for (h in 8..21) {
        add(String.format("%02d:00", h))
        add(String.format("%02d:30", h))
    }
}

private data class AvailabilitySchedule(val weekdays:Set<Int>, val groups:List<Pair<Set<Int>,List<String>>>)
private fun scheduleFromJson(settingsJson:String):AvailabilitySchedule = runCatching {
    val root=JsonParser.parseString(settingsJson.ifBlank { "{}" }).asJsonObject
    val s=root.getAsJsonObject("schedule") ?: root
    val weekdays=s.getAsJsonArray("weekdays")?.mapNotNull { runCatching{it.asInt}.getOrNull() }?.toSet() ?: (0..6).toSet()
    val groups=s.getAsJsonArray("work_time_groups")?.mapNotNull { e ->
        runCatching {
            val o=e.asJsonObject
            val days=o.getAsJsonArray("days")?.map{it.asInt}?.toSet() ?: emptySet()
            val times=o.getAsJsonArray("times")?.map{it.asString} ?: emptyList()
            days to times
        }.getOrNull()
    } ?: emptyList()
    AvailabilitySchedule(weekdays,groups)
}.getOrElse { AvailabilitySchedule((0..6).toSet(),emptyList()) }

private fun scheduledTimes(date:LocalDate, settingsJson:String):List<String>{
    val s=scheduleFromJson(settingsJson)
    val dow=date.dayOfWeek.value % 7
    val group=s.groups.firstOrNull { dow in it.first }
    if(group!=null && group.second.isNotEmpty()) return group.second.distinct().sorted()
    return if(dow in s.weekdays) listOf("10:00","13:00","16:00","19:00") else emptyList()
}

@Composable
fun AvailabilityOverlay(
    initialDate: LocalDate = LocalDate.now(availabilityZone),
    initialTime: String? = null,
    settingsJson: String = "{}",
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
    var showAddTime by remember { mutableStateOf(false) }

    val manualTimes = overrides.filter { runCatching { Instant.parse(it.slotStart).atZone(availabilityZone).toLocalDate()==date }.getOrDefault(false) }
        .map { Instant.parse(it.slotStart).atZone(availabilityZone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val candidateTimes = (scheduledTimes(date,settingsJson)+manualTimes+listOfNotNull(initialTime)).distinct().sorted()

    val slots = candidateTimes.map { tm ->
        val start = LocalDateTime.parse("${date}T${tm}").atZone(availabilityZone).toInstant()
        val end = start.plus(Duration.ofHours(3))
        val iso = start.toString()
        val override = overrides.lastOrNull { sameMinute(it.slotStart, iso) }
        val occupied = appointments.any { a ->
            a.status != "cancelled" && a.status != "canceled" && runCatching {
                val aStart = Instant.parse(a.startsAt)
                val aEnd = Instant.parse(a.endsAt)
                aStart < end && aEnd > start
            }.getOrDefault(false)
        }
        val past = start <= Instant.now()
        val base = tm in scheduledTimes(date,settingsJson)
        val available = !past && !occupied && (override?.available ?: base)
        WindowState(tm, iso, available, occupied, past, override, base)
    }
    val availableTimes = slots.filter { it.available }.map { it.time }
    val shareText = buildWindowsText(date, availableTimes)

    if(showAddTime){
        AlertDialog(
            onDismissRequest={showAddTime=false},
            title={Text("Добавить время для записи")},
            text={
                LazyColumn(Modifier.heightIn(max=420.dp)){
                    items(allHalfHourTimes.filter{it !in candidateTimes}){tm->
                        Row(Modifier.fillMaxWidth().clickable{
                            val iso=LocalDateTime.parse("${date}T${tm}").atZone(availabilityZone).toInstant().toString()
                            scope.launch{repo.setAvailability(iso,true);launch{runCatching{repo.flush(pin)}}}
                            showAddTime=false
                        }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(tm,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showAddTime=false}){Text("Закрыть")}}
        )
    }

    FullOverlay(onClose) {
        Text("Доступное время", style = MaterialTheme.typography.headlineSmall)
        Text("Нажатие из календаря открывает редактирование этого дня", style = MaterialTheme.typography.bodyMedium)
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
        Spacer(Modifier.height(6.dp))
        Button(onClick={showAddTime=true},modifier=Modifier.fillMaxWidth()){Text("+ Добавить время")}
        Spacer(Modifier.height(8.dp))

        if(slots.isEmpty()){
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
                Text("На этот день время для записи не задано")
            }
        }else{
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
                                        slot.available && slot.override?.available == true -> "Открыто вручную"
                                        slot.available -> "Доступно по расписанию"
                                        slot.override?.available == false -> "Закрыто вручную"
                                        else -> "Недоступно"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (slot.override?.pending == true) Text("Сохраняется…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (!slot.past && !slot.occupied) {
                                if (slot.available) {
                                    OutlinedButton(onClick = {
                                        scope.launch { repo.setAvailability(slot.iso, false); launch { runCatching { repo.flush(pin) } } }
                                    }) { Text("Закрыть") }
                                } else {
                                    Button(onClick = {
                                        scope.launch { repo.setAvailability(slot.iso, true); launch { runCatching { repo.flush(pin) } } }
                                    }) { Text("Открыть") }
                                }
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
                    Button(onClick={
                        val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Свободные окошки",shareText));copied=true
                    },enabled=availableTimes.isNotEmpty(),modifier=Modifier.weight(1f)){Text(if(copied)"Скопировано ✓" else "Копировать")}
                    OutlinedButton(onClick={
                        val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,shareText)}
                        context.startActivity(Intent.createChooser(intent,"Отправить окошки"))
                    },enabled=availableTimes.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Поделиться")}
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Изменения применяются сразу и синхронизируются с Slotelly.", style = MaterialTheme.typography.bodySmall)
    }
}

private data class WindowState(
    val time:String,
    val iso:String,
    val available:Boolean,
    val occupied:Boolean,
    val past:Boolean,
    val override:AvailabilityOverrideEntity?,
    val base:Boolean
)

private fun buildWindowsText(date:LocalDate,times:List<String>):String{
    val prettyDate=date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", availabilityRu))
    return if(times.isEmpty()) "Свободных окошек на $prettyDate пока нет" else buildString {
        append("Свободные окошки ✨\n")
        append(prettyDate.replaceFirstChar { if(it.isLowerCase()) it.titlecase(availabilityRu) else it.toString() })
        append("\n\n")
        times.forEach { append("• $it\n") }
        append("\nЗаписаться онлайн: $bookingLink")
    }
}

private fun sameMinute(a: String, b: String): Boolean = runCatching {
    kotlin.math.abs(Instant.parse(a).epochSecond - Instant.parse(b).epochSecond) < 60
}.getOrDefault(false)
