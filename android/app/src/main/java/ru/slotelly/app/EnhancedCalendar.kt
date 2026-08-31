package ru.slotelly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private fun ecDate(iso: String): LocalDate = Instant.parse(iso).atZone(EC_ZONE).toLocalDate()
private fun ecTime(iso: String): LocalTime = Instant.parse(iso).atZone(EC_ZONE).toLocalTime()
private fun ecServices(a: AppointmentEntity): String = runCatching {
    com.google.gson.JsonParser.parseString(a.servicesJson).asJsonArray.mapNotNull { e ->
        val o = e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")

@Composable
fun EnhancedCalendarScreen(
    appointments: List<AppointmentEntity>,
    onOpen: (AppointmentEntity) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    var focus by rememberSaveable { mutableStateOf(LocalDate.now(EC_ZONE).toString()) }
    val date = runCatching { LocalDate.parse(focus) }.getOrElse { LocalDate.now(EC_ZONE) }

    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { focus = date.minusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }) { Text("‹") }
            Text(
                if (mode == CalendarMode.WEEK) {
                    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    "${monday.format(DateTimeFormatter.ofPattern("dd.MM"))}–${monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}"
                } else date.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", EC_RU)).replace(".", ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { focus = LocalDate.now(EC_ZONE).toString() }) { Text("Сегодня") }
            TextButton(onClick = { focus = date.plusDays(if (mode == CalendarMode.WEEK) 7 else 1).toString() }) { Text("›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == CalendarMode.WEEK, onClick = { mode = CalendarMode.WEEK }, label = { Text("Неделя") })
            FilterChip(selected = mode == CalendarMode.DAY, onClick = { mode = CalendarMode.DAY }, label = { Text("День") })
        }
        Spacer(Modifier.height(4.dp))
        if (mode == CalendarMode.WEEK) {
            HourWeekGrid(appointments, date, Modifier.weight(1f), onOpen)
        } else {
            HourDayGrid(appointments, date, Modifier.weight(1f), onOpen)
        }
    }
}

@Composable
private fun HourWeekGrid(
    appointments: List<AppointmentEntity>,
    focus: LocalDate,
    modifier: Modifier,
    onOpen: (AppointmentEntity) -> Unit
) {
    val monday = focus.minusDays((focus.dayOfWeek.value - 1).toLong())
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(34.dp)) {
            Spacer(Modifier.width(28.dp))
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.format(DateTimeFormatter.ofPattern("EE", EC_RU)).replace(".", ""), style = MaterialTheme.typography.labelSmall)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            (START_HOUR until END_HOUR).forEach { hour ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Text("$hour", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp).padding(top = 2.dp))
                    (0..6).forEach { i ->
                        val d = monday.plusDays(i.toLong())
                        val rows = appointments.filter { a ->
                            ecDate(a.startsAt) == d && ecTime(a.startsAt).hour == hour
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .padding(0.5.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f))
                        ) {
                            rows.take(2).forEachIndexed { index, a ->
                                Surface(
                                    tonalElevation = if (a.pending) 3.dp else 1.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = (index * 18).dp, start = 1.dp, end = 1.dp).clickable { onOpen(a) }
                                ) {
                                    Column(Modifier.padding(horizontal = 2.dp, vertical = 1.dp)) {
                                        Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun HourDayGrid(
    appointments: List<AppointmentEntity>,
    date: LocalDate,
    modifier: Modifier,
    onOpen: (AppointmentEntity) -> Unit
) {
    val hourHeight: Dp = 64.dp
    val totalHeight = hourHeight * (END_HOUR - START_HOUR)
    val rows = appointments.filter { ecDate(it.startsAt) == date }.sortedBy { it.startsAt }
    val scroll = rememberScrollState()

    Box(modifier.fillMaxWidth().verticalScroll(scroll).height(totalHeight)) {
        Column(Modifier.fillMaxWidth()) {
            (START_HOUR until END_HOUR).forEach { h ->
                Row(Modifier.height(hourHeight).fillMaxWidth()) {
                    Text(String.format("%02d:00", h), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp).padding(top = 2.dp))
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .18f))) {
                        HorizontalDivider(Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
        rows.forEach { a ->
            val start = ecTime(a.startsAt)
            val end = ecTime(a.endsAt)
            val startMinutes = ((start.hour - START_HOUR) * 60 + start.minute).coerceAtLeast(0)
            val duration = Duration.between(start, end).toMinutes().coerceAtLeast(30)
            val y = hourHeight * (startMinutes / 60f)
            val h = (hourHeight * (duration / 60f)).coerceAtLeast(42.dp)
            Surface(
                tonalElevation = if (a.pending) 5.dp else 2.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 54.dp, end = 4.dp).offset(y = y).height(h).fillMaxWidth().clickable { onOpen(a) }
            ) {
                Column(Modifier.padding(6.dp)) {
                    Text("${start.format(DateTimeFormatter.ofPattern("HH:mm"))}–${end.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${a.clientName}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val services = ecServices(a)
                    if (services.isNotBlank()) Text(services, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val now = ZonedDateTime.now(EC_ZONE)
        if (date == now.toLocalDate() && now.hour in START_HOUR until END_HOUR) {
            val minutes = (now.hour - START_HOUR) * 60 + now.minute
            val y = hourHeight * (minutes / 60f)
            HorizontalDivider(Modifier.padding(start = 48.dp).offset(y = y), thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
