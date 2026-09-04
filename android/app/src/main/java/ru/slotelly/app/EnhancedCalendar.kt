package ru.slotelly.app

import androidx.compose.foundation.BorderStroke
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
import com.google.gson.JsonParser
import ru.slotelly.app.data.AppointmentEntity
import ru.slotelly.app.data.AvailabilityOverrideEntity
import ru.slotelly.app.data.CalendarBlockEntity
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val EC_ZONE = ZoneId.of("Europe/Moscow")
private val EC_RU = Locale("ru", "RU")
private const val START_HOUR = 8
private const val END_HOUR = 22
private const val TOTAL_MINUTES = (END_HOUR - START_HOUR) * 60

private val CAL_GRID = Color(0xFFF5F3F5)
private val CAL_WEEKEND = Color(0xFFFAF7FA)
private val CAL_TODAY = Color(0xFF7A3157)
private val CAL_TODAY_BG = Color(0xFFF4EAEE)
private val CAL_BOOKING = Color(0xFFE6DDF6)
private val CAL_PAID = Color(0xFFDDF3E8)
private val CAL_UNPAID = Color(0xFFFFEBD8)
private val CAL_PENDING = Color(0xFFE2E8FF)
private val CAL_AVAILABLE = Color(0xFFE8F4ED)
private val CAL_AVAILABLE_BORDER = Color(0xFF6E9A80)
private val CAL_BREAK = Color(0xFFF1ECE8)

private data class WorkGroup(val days: Set<Int>, val times: List<String>)
private data class WorkSchedule(val weekdays: Set<Int>, val groups: List<WorkGroup>, val workdayOverrides: Set<String>)

private fun ecDate(iso: String): LocalDate = Instant.parse(iso).atZone(EC_ZONE).toLocalDate()
private fun ecTime(iso: String): LocalTime = Instant.parse(iso).atZone(EC_ZONE).toLocalTime()
private fun ecMinutes(iso: String): Int { val t=ecTime(iso); return t.hour*60+t.minute }
private fun ecDuration(a: AppointmentEntity): Long = Duration.between(Instant.parse(a.startsAt), Instant.parse(a.endsAt)).toMinutes().coerceAtLeast(30)
private fun ecServices(a: AppointmentEntity): String = runCatching {
    JsonParser.parseString(a.servicesJson).asJsonArray.mapNotNull { e ->
        val o=e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")
private fun appointmentColor(a: AppointmentEntity): Color = when {
    a.pending -> CAL_PENDING
    a.status == "completed_paid" -> CAL_PAID
    a.status == "completed_unpaid" -> CAL_UNPAID
    else -> CAL_BOOKING
}

private fun parseSchedule(settingsJson: String): WorkSchedule = runCatching {
    val root=JsonParser.parseString(settingsJson.ifBlank { "{}" }).asJsonObject
    val s=(root.getAsJsonObject("schedule") ?: root)
    val weekdays=s.getAsJsonArray("weekdays")?.mapNotNull { runCatching { it.asInt }.getOrNull() }?.toSet() ?: (0..6).toSet()
    val groups=s.getAsJsonArray("work_time_groups")?.mapNotNull { e ->
        runCatching {
            val o=e.asJsonObject
            WorkGroup(
                o.getAsJsonArray("days")?.map { it.asInt }?.toSet() ?: emptySet(),
                o.getAsJsonArray("times")?.map { it.asString } ?: emptyList()
            )
        }.getOrNull()
    } ?: emptyList()
    val overrides=s.getAsJsonArray("workday_overrides")?.map { it.asString }?.toSet() ?: emptySet()
    WorkSchedule(weekdays,groups,overrides)
}.getOrElse { WorkSchedule((0..6).toSet(),emptyList(),emptySet()) }

private fun baseTimes(date: LocalDate, schedule: WorkSchedule): List<String> {
    val dow=date.dayOfWeek.value % 7
    val group=schedule.groups.firstOrNull { dow in it.days }
    if(group!=null && group.times.isNotEmpty()) return group.times.distinct().sorted()
    return if(dow in schedule.weekdays) listOf("10:00","13:00","16:00","19:00") else emptyList()
}
private fun blockDate(b: CalendarBlockEntity): LocalDate = ecDate(b.startsAt)
private fun isDayOff(date: LocalDate, schedule: WorkSchedule, blocks: List<CalendarBlockEntity>): Boolean {
    val dow=date.dayOfWeek.value % 7
    val weekly=dow !in schedule.weekdays && date.toString() !in schedule.workdayOverrides
    val manual=blocks.any { it.label.equals("Выходной",true) && blockDate(it)==date }
    return weekly || manual
}
private fun overlaps(start:Int,end:Int,aStart:Int,aEnd:Int):Boolean = start<aEnd && end>aStart
private fun freeTimes(date: LocalDate, schedule: WorkSchedule, appointments: List<AppointmentEntity>, blocks: List<CalendarBlockEntity>, overrides: List<AvailabilityOverrideEntity>): List<String> {
    if(isDayOff(date,schedule,blocks)) return emptyList()
    val manualOn=overrides.filter { it.available && ecDate(it.slotStart)==date }.map { ecTime(it.slotStart).format(DateTimeFormatter.ofPattern("HH:mm")) }
    val all=(baseTimes(date,schedule)+manualOn).distinct().sorted()
    val dayAppts=appointments.filter { ecDate(it.startsAt)==date && it.status!="cancelled" && it.status!="canceled" }
    val dayBlocks=blocks.filter { blockDate(it)==date }
    return all.filter { t ->
        val p=LocalTime.parse(t); val m=p.hour*60+p.minute
        val forcedOff=overrides.any { !it.available && ecDate(it.slotStart)==date && ecMinutes(it.slotStart)==m }
        !forcedOff && dayAppts.none { overlaps(m,m+30,ecMinutes(it.startsAt),ecMinutes(it.endsAt)) } && dayBlocks.none { overlaps(m,m+30,ecMinutes(it.startsAt),ecMinutes(it.endsAt)) }
    }
}

@Composable
fun EnhancedCalendarScreen(
    appointments: List<AppointmentEntity>,
    blocks: List<CalendarBlockEntity>,
    overrides: List<AvailabilityOverrideEntity>,
    settingsJson: String,
    onOpen: (AppointmentEntity) -> Unit,
    onEditAvailability: (LocalDate, String?) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    var focus by rememberSaveable { mutableStateOf(LocalDate.now(EC_ZONE).toString()) }
    val date=runCatching { LocalDate.parse(focus) }.getOrElse { LocalDate.now(EC_ZONE) }
    val schedule=remember(settingsJson){parseSchedule(settingsJson)}
    Column(Modifier.fillMaxSize().padding(horizontal=10.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={focus=date.minusDays(if(mode==CalendarMode.WEEK)7 else 1).toString()},modifier=Modifier.size(36.dp)){Text("‹",style=MaterialTheme.typography.titleLarge)}
            Text(if(mode==CalendarMode.WEEK){val monday=date.minusDays((date.dayOfWeek.value-1).toLong());"${monday.format(DateTimeFormatter.ofPattern("dd.MM"))}–${monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}"}else date.format(DateTimeFormatter.ofPattern("dd MMM, EEE",EC_RU)).replace(".",""),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).padding(start=4.dp))
            TextButton(onClick={focus=LocalDate.now(EC_ZONE).toString()}){Text("Сегодня")}
            IconButton(onClick={focus=date.plusDays(if(mode==CalendarMode.WEEK)7 else 1).toString()},modifier=Modifier.size(36.dp)){Text("›",style=MaterialTheme.typography.titleLarge)}
        }
        Row(Modifier.fillMaxWidth().height(40.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=mode==CalendarMode.WEEK,onClick={mode=CalendarMode.WEEK},label={Text("Неделя",fontWeight=FontWeight.SemiBold)},modifier=Modifier.weight(1f))
            FilterChip(selected=mode==CalendarMode.DAY,onClick={mode=CalendarMode.DAY},label={Text("День",fontWeight=FontWeight.SemiBold)},modifier=Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        if(mode==CalendarMode.WEEK) WeekTimeline(appointments,blocks,overrides,schedule,date,Modifier.weight(1f),onOpen,onEditAvailability)
        else DayTimeline(appointments,blocks,overrides,schedule,date,Modifier.weight(1f),onOpen,onEditAvailability)
    }
}

@Composable
private fun WeekTimeline(
    appointments:List<AppointmentEntity>, blocks:List<CalendarBlockEntity>, overrides:List<AvailabilityOverrideEntity>, schedule:WorkSchedule,
    focus:LocalDate, modifier:Modifier, onOpen:(AppointmentEntity)->Unit, onEditAvailability:(LocalDate,String?)->Unit
){
    val monday=focus.minusDays((focus.dayOfWeek.value-1).toLong())
    val today=LocalDate.now(EC_ZONE)
    val labelW=30.dp
    Column(modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().height(38.dp)){
            Spacer(Modifier.width(labelW))
            (0..6).forEach{i->
                val d=monday.plusDays(i.toLong());val now=d==today
                Column(Modifier.weight(1f).fillMaxHeight().background(if(now)CAL_TODAY_BG else Color.Transparent,RoundedCornerShape(10.dp)).clickable{onEditAvailability(d,null)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Text(d.format(DateTimeFormatter.ofPattern("EE",EC_RU)).replace(".",""),style=MaterialTheme.typography.labelSmall,color=if(now)CAL_TODAY else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.dayOfMonth.toString(),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=if(now)CAL_TODAY else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)){
            val gridH=maxHeight
            val contentW=maxWidth-labelW
            val dayW=contentW/7f
            Column(Modifier.fillMaxSize()){
                (START_HOUR until END_HOUR).forEach{h->
                    Row(Modifier.weight(1f).fillMaxWidth()){
                        Text(h.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(labelW).padding(top=2.dp))
                        (0..6).forEach{i->
                            val d=monday.plusDays(i.toLong());val now=d==today;val off=isDayOff(d,schedule,blocks)
                            Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(if(off)Color(0xFFF0ECEE) else if(now)CAL_TODAY_BG.copy(alpha=.45f) else if(d.dayOfWeek==DayOfWeek.SATURDAY||d.dayOfWeek==DayOfWeek.SUNDAY)CAL_WEEKEND else CAL_GRID,RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
            (0..6).forEach{i->
                val d=monday.plusDays(i.toLong())
                freeTimes(d,schedule,appointments,blocks,overrides).forEach{t->
                    val p=LocalTime.parse(t);val m=p.hour*60+p.minute
                    if(m in START_HOUR*60 until END_HOUR*60){
                        val y=gridH*((m-START_HOUR*60)/TOTAL_MINUTES.toFloat())
                        Surface(color=CAL_AVAILABLE,border=BorderStroke(.7.dp,CAL_AVAILABLE_BORDER.copy(alpha=.7f)),shape=RoundedCornerShape(5.dp),modifier=Modifier.offset(x=labelW+dayW*i,y=y).width(dayW).height(22.dp).padding(horizontal=1.dp).clickable{onEditAvailability(d,t)}){
                            Box(contentAlignment=Alignment.Center){Text(t,style=MaterialTheme.typography.labelSmall,color=Color(0xFF3E6C52),fontWeight=FontWeight.SemiBold)}
                        }
                    }
                }
            }
            appointments.filter{it.status!="cancelled"&&it.status!="canceled"}.forEach{a->
                val d=ecDate(a.startsAt);val i=ChronoUnit.DAYS.between(monday,d).toInt()
                if(i in 0..6){
                    val sm=(ecMinutes(a.startsAt)-START_HOUR*60).coerceAtLeast(0);val dur=ecDuration(a).toInt()
                    if(sm<TOTAL_MINUTES){
                        val y=gridH*(sm/TOTAL_MINUTES.toFloat());val hh=(gridH*(dur/TOTAL_MINUTES.toFloat())).coerceAtLeast(30.dp)
                        Surface(color=appointmentColor(a),shape=RoundedCornerShape(7.dp),tonalElevation=1.dp,modifier=Modifier.offset(x=labelW+dayW*i,y=y).width(dayW).height(hh).padding(horizontal=1.dp).clickable{onOpen(a)}){
                            Column(Modifier.padding(horizontal=4.dp,vertical=3.dp)){
                                Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,maxLines=1)
                                Text(a.clientName,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(hh>44.dp){val s=ecServices(a);if(s.isNotBlank())Text(s,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(
    appointments:List<AppointmentEntity>,blocks:List<CalendarBlockEntity>,overrides:List<AvailabilityOverrideEntity>,schedule:WorkSchedule,
    date:LocalDate,modifier:Modifier,onOpen:(AppointmentEntity)->Unit,onEditAvailability:(LocalDate,String?)->Unit
){
    val labelW=44.dp
    val dayAppts=appointments.filter{ecDate(it.startsAt)==date&&it.status!="cancelled"&&it.status!="canceled"}
    val frees=freeTimes(date,schedule,appointments,blocks,overrides)
    val dayBlocks=blocks.filter{blockDate(it)==date&&it.source=="manual_break"}
    BoxWithConstraints(modifier.fillMaxWidth()){
        val gridH=maxHeight
        Column(Modifier.fillMaxSize()){
            (START_HOUR until END_HOUR).forEach{h->
                Row(Modifier.weight(1f).fillMaxWidth()){
                    Text(String.format("%02d",h),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(labelW).padding(top=2.dp))
                    Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(CAL_GRID,RoundedCornerShape(5.dp)))
                }
            }
        }
        frees.forEach{t->
            val p=LocalTime.parse(t);val m=p.hour*60+p.minute
            if(m in START_HOUR*60 until END_HOUR*60){
                val y=gridH*((m-START_HOUR*60)/TOTAL_MINUTES.toFloat())
                Surface(color=CAL_AVAILABLE,border=BorderStroke(1.dp,CAL_AVAILABLE_BORDER),shape=RoundedCornerShape(8.dp),modifier=Modifier.offset(x=labelW,y=y).fillMaxWidth().height(30.dp).padding(end=5.dp).clickable{onEditAvailability(date,t)}){
                    Row(Modifier.fillMaxSize().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){Text(t,fontWeight=FontWeight.Bold,color=Color(0xFF3E6C52));Spacer(Modifier.width(8.dp));Text("Доступно для записи · изменить",style=MaterialTheme.typography.labelSmall,color=Color(0xFF3E6C52))}
                }
            }
        }
        dayBlocks.forEach{b->
            val sm=ecMinutes(b.startsAt)-START_HOUR*60;val dur=(ecMinutes(b.endsAt)-ecMinutes(b.startsAt)).coerceAtLeast(15)
            if(sm in 0 until TOTAL_MINUTES){
                val y=gridH*(sm/TOTAL_MINUTES.toFloat());val hh=(gridH*(dur/TOTAL_MINUTES.toFloat())).coerceAtLeast(28.dp)
                Surface(color=CAL_BREAK,shape=RoundedCornerShape(8.dp),modifier=Modifier.offset(x=labelW,y=y).fillMaxWidth().height(hh).padding(end=5.dp)){
                    Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)){Text("${ecTime(b.startsAt).format(DateTimeFormatter.ofPattern("HH:mm"))}–${ecTime(b.endsAt).format(DateTimeFormatter.ofPattern("HH:mm"))} · Перерыв",fontWeight=FontWeight.SemiBold);if(b.label.isNotBlank())Text(b.label,style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
        dayAppts.forEach{a->
            val sm=ecMinutes(a.startsAt)-START_HOUR*60;val dur=ecDuration(a).toInt()
            if(sm<TOTAL_MINUTES){
                val y=gridH*(sm.coerceAtLeast(0)/TOTAL_MINUTES.toFloat());val hh=(gridH*(dur/TOTAL_MINUTES.toFloat())).coerceAtLeast(40.dp)
                Surface(color=appointmentColor(a),shape=RoundedCornerShape(9.dp),tonalElevation=2.dp,modifier=Modifier.offset(x=labelW,y=y).fillMaxWidth().height(hh).padding(end=5.dp).clickable{onOpen(a)}){
                    Column(Modifier.padding(horizontal=9.dp,vertical=5.dp)){
                        Text("${ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm"))}–${ecTime(a.endsAt).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${a.clientName}",fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        val s=ecServices(a);if(s.isNotBlank())Text(s,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
