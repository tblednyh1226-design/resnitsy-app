package ru.slotelly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.slotelly.app.data.AppointmentEntity
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EC_ZONE = ZoneId.of("Europe/Moscow")
private val EC_RU = Locale("ru", "RU")
private const val START_HOUR = 8
private const val END_HOUR = 22

private val CAL_WEEKEND = Color(0xFFFFF1F5)
private val CAL_TODAY = Color(0xFF9C3F68)
private val CAL_BOOKING = Color(0xFFFFD9E6)
private val CAL_PAID = Color(0xFFD8F3E5)
private val CAL_UNPAID = Color(0xFFFFE3C7)
private val CAL_CANCELLED = Color(0xFFE9E3E6)
private val CAL_PENDING = Color(0xFFEBDDFF)

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

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { focus = date.minusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }, modifier = Modifier.size(38.dp)) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(6.dp))
            Text(
                if (mode == CalendarMode.WEEK) {
                    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    "${monday.format(DateTimeFormatter.ofPattern("dd.MM"))}–${monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}"
                } else date.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", EC_RU)).replace(".", ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            )
            TextButton(onClick = { focus = LocalDate.now(EC_ZONE).toString() }) { Text("Сегодня", fontWeight = FontWeight.SemiBold) }
            FilledTonalIconButton(onClick = { focus = date.plusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }, modifier = Modifier.size(38.dp)) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == CalendarMode.WEEK, onClick = { mode = CalendarMode.WEEK }, label = { Text("Неделя", fontWeight = FontWeight.SemiBold) }, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == CalendarMode.DAY, onClick = { mode = CalendarMode.DAY }, label = { Text("День", fontWeight = FontWeight.SemiBold) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        if (mode == CalendarMode.WEEK) HourWeekGrid(appointments, date, Modifier.weight(1f), onOpen)
        else HourDayGrid(appointments, date, Modifier.weight(1f), onOpen)
    }
}

@Composable
private fun HourWeekGrid(appointments: List<AppointmentEntity>, focus: LocalDate, modifier: Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val monday = focus.minusDays((focus.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now(EC_ZONE)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            Spacer(Modifier.width(30.dp))
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val isToday = d == today
                val isWeekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp).background(if (isWeekend) CAL_WEEKEND else Color.Transparent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isToday) {
                        Surface(color = CAL_TODAY, contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.format(DateTimeFormatter.ofPattern("EE", EC_RU)).replace(".", ""), style = MaterialTheme.typography.labelSmall)
                                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.format(DateTimeFormatter.ofPattern("EE", EC_RU)).replace(".", ""), style = MaterialTheme.typography.labelSmall, color = if (isWeekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            (START_HOUR until END_HOUR).forEach { hour ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Text("$hour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp).padding(top = 3.dp))
                    (0..6).forEach { i ->
                        val d = monday.plusDays(i.toLong())
                        val isToday = d == today
                        val isWeekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                        val rows = appointments.filter { a -> ecDate(a.startsAt) == d && ecTime(a.startsAt).hour == hour }
                        val cell = when {
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .20f)
                            isWeekend -> CAL_WEEKEND.copy(alpha = .75f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .18f)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().padding(0.5.dp).background(cell, RoundedCornerShape(3.dp))) {
                            rows.take(2).forEachIndexed { index, a ->
                                Surface(
                                    tonalElevation = if (a.pending) 4.dp else 2.dp,
                                    shadowElevation = if (a.pending) 2.dp else 0.dp,
                                    shape = RoundedCornerShape(7.dp),
                                    color = appointmentColor(a),
                                    modifier = Modifier.fillMaxWidth().padding(top = (index * 20).dp, start = 1.dp, end = 1.dp).clickable { onOpen(a) }
                                ) {
                                    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(a.clientName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                    }
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
private fun HourDayGrid(appointments: List<AppointmentEntity>, date: LocalDate, modifier: Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val hourHeight: Dp = 68.dp
    val totalHeight = hourHeight * (END_HOUR - START_HOUR)
    val rows = appointments.filter { ecDate(it.startsAt) == date }.sortedBy { it.startsAt }
    val scroll = rememberScrollState()
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    Box(modifier.fillMaxWidth().verticalScroll(scroll).height(totalHeight).background(if (isWeekend) CAL_WEEKEND.copy(alpha = .45f) else Color.Transparent)) {
        Column(Modifier.fillMaxWidth()) {
            (START_HOUR until END_HOUR).forEach { h ->
                Row(Modifier.height(hourHeight).fillMaxWidth()) {
                    Text(String.format("%02d:00", h), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(54.dp).padding(top = 4.dp))
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .12f))) { HorizontalDivider(Modifier.align(Alignment.TopCenter), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f)) }
                }
            }
        }
        rows.forEach { a ->
            val start = ecTime(a.startsAt); val end = ecTime(a.endsAt)
            val startMinutes = ((start.hour - START_HOUR) * 60 + start.minute).coerceAtLeast(0)
            val duration = Duration.between(start, end).toMinutes().coerceAtLeast(30)
            val y = hourHeight * (startMinutes / 60f)
            val h = (hourHeight * (duration / 60f)).coerceAtLeast(44.dp)
            Surface(
                tonalElevation = if (a.pending) 5.dp else 3.dp,
                shadowElevation = 1.dp,
                shape = RoundedCornerShape(12.dp),
                color = appointmentColor(a),
                modifier = Modifier.padding(start = 56.dp, end = 6.dp).offset(y = y).height(h).fillMaxWidth().clickable { onOpen(a) }
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text("${start.format(DateTimeFormatter.ofPattern("HH:mm"))}–${end.format(DateTimeFormatter.ofPattern("HH:mm"))}  ${a.clientName}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val services = ecServices(a)
                    if (services.isNotBlank()) Text(services, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val now = ZonedDateTime.now(EC_ZONE)
        if (date == now.toLocalDate() && now.hour in START_HOUR until END_HOUR) {
            val minutes = (now.hour - START_HOUR) * 60 + now.minute
            val y = hourHeight * (minutes / 60f)
            HorizontalDivider(Modifier.padding(start = 50.dp).offset(y = y), thickness = 2.dp, color = CAL_TODAY)
        }
    }
}
