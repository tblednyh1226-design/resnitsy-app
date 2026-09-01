package ru.slotelly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.slotelly.app.data.AppointmentEntity
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EC_ZONE = ZoneId.of("Europe/Moscow")
private val EC_RU = Locale("ru", "RU")
private const val START_HOUR = 8
private const val END_HOUR = 22

private val CAL_GRID = Color(0xFFF4F1F5)
private val CAL_WEEKEND = Color(0xFFF8F4FA)
private val CAL_TODAY = Color(0xFF7A3157)
private val CAL_TODAY_BG = Color(0xFFF3E8EE)
private val CAL_BOOKING = Color(0xFFEDE4FF)
private val CAL_PAID = Color(0xFFDDF3E8)
private val CAL_UNPAID = Color(0xFFFFEBD8)
private val CAL_CANCELLED = Color(0xFFE9E6E8)
private val CAL_PENDING = Color(0xFFE5E9FF)

private fun ecDate(iso: String): LocalDate = Instant.parse(iso).atZone(EC_ZONE).toLocalDate()
private fun ecTime(iso: String): LocalTime = Instant.parse(iso).atZone(EC_ZONE).toLocalTime()
private fun ecServices(a: AppointmentEntity): String = runCatching {
    com.google.gson.JsonParser.parseString(a.servicesJson).asJsonArray.mapNotNull { e ->
        val o = e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")
private fun appointmentColor(a: AppointmentEntity): Color = when {
    a.pending -> CAL_PENDING
    a.status == "completed_paid" -> CAL_PAID
    a.status == "completed_unpaid" -> CAL_UNPAID
    a.status == "cancelled" -> CAL_CANCELLED
    else -> CAL_BOOKING
}

@Composable
fun EnhancedCalendarScreen(appointments: List<AppointmentEntity>, onOpen: (AppointmentEntity) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    var focus by rememberSaveable { mutableStateOf(LocalDate.now(EC_ZONE).toString()) }
    val date = runCatching { LocalDate.parse(focus) }.getOrElse { LocalDate.now(EC_ZONE) }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { focus = date.minusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }, modifier = Modifier.size(36.dp)) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(
                if (mode == CalendarMode.WEEK) {
                    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    "${monday.format(DateTimeFormatter.ofPattern("dd.MM"))}–${monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}"
                } else date.format(DateTimeFormatter.ofPattern("dd MMM, EEE", EC_RU)).replace(".", ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            TextButton(onClick = { focus = LocalDate.now(EC_ZONE).toString() }) { Text("Сегодня") }
            IconButton(onClick = { focus = date.plusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }, modifier = Modifier.size(36.dp)) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == CalendarMode.WEEK, onClick = { mode = CalendarMode.WEEK }, label = { Text("Неделя", fontWeight = FontWeight.SemiBold) }, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == CalendarMode.DAY, onClick = { mode = CalendarMode.DAY }, label = { Text("День", fontWeight = FontWeight.SemiBold) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        if (mode == CalendarMode.WEEK) CompactWeekGrid(appointments, date, Modifier.weight(1f), onOpen)
        else CompactDayGrid(appointments, date, Modifier.weight(1f), onOpen)
    }
}

@Composable
private fun CompactWeekGrid(appointments: List<AppointmentEntity>, focus: LocalDate, modifier: Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val monday = focus.minusDays((focus.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now(EC_ZONE)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            Spacer(Modifier.width(28.dp))
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val isToday = d == today
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(if (isToday) CAL_TODAY_BG else Color.Transparent, RoundedCornerShape(10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(d.format(DateTimeFormatter.ofPattern("EE", EC_RU)).replace(".", ""), style = MaterialTheme.typography.labelSmall, color = if (isToday) CAL_TODAY else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isToday) CAL_TODAY else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        (START_HOUR until END_HOUR).forEach { hour ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Text(hour.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp).padding(top = 2.dp))
                (0..6).forEach { i ->
                    val d = monday.plusDays(i.toLong())
                    val isToday = d == today
                    val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                    val rows = appointments.filter { a -> ecDate(a.startsAt) == d && ecTime(a.startsAt).hour == hour }
                    val bg = when {
                        isToday -> CAL_TODAY_BG.copy(alpha = .55f)
                        weekend -> CAL_WEEKEND
                        else -> CAL_GRID
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(bg, RoundedCornerShape(4.dp))) {
                        rows.firstOrNull()?.let { a ->
                            Surface(
                                color = appointmentColor(a),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxSize().padding(1.dp).clickable { onOpen(a) }
                            ) {
                                Column(Modifier.padding(horizontal = 3.dp, vertical = 1.dp)) {
                                    Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(a.clientName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDayGrid(appointments: List<AppointmentEntity>, date: LocalDate, modifier: Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val rows = appointments.filter { ecDate(it.startsAt) == date }.sortedBy { it.startsAt }
    Column(modifier.fillMaxWidth()) {
        (START_HOUR until END_HOUR).forEach { hour ->
            val items = rows.filter { ecTime(it.startsAt).hour == hour }
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(String.format("%02d", hour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = .5.dp).background(CAL_GRID, RoundedCornerShape(6.dp))) {
                    if (items.isEmpty()) {
                        HorizontalDivider(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline.copy(alpha = .10f))
                    } else {
                        val a = items.first()
                        Surface(color = appointmentColor(a), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize().clickable { onOpen(a) }) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.clientName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val services = ecServices(a)
                                    if (services.isNotBlank()) Text(services, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
