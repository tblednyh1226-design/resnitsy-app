from pathlib import Path

cal = Path('app/src/main/java/ru/slotelly/app/EnhancedCalendar.kt')
c = cal.read_text(encoding='utf-8')
start = c.index('@Composable\nprivate fun DayTimeline(')
new_day = r'''@Composable
private fun DayTimeline(
    appointments:List<AppointmentEntity>,blocks:List<CalendarBlockEntity>,overrides:List<AvailabilityOverrideEntity>,schedule:WorkSchedule,
    date:LocalDate,modifier:Modifier,onOpen:(AppointmentEntity)->Unit,onEditAvailability:(LocalDate,String?)->Unit
){
    val labelW=34.dp
    val today=LocalDate.now(EC_ZONE)
    val now=date==today
    val off=isDayOff(date,schedule,blocks)
    val dayAppts=appointments.filter{ecDate(it.startsAt)==date&&it.status!="cancelled"&&it.status!="canceled"}
    val frees=freeTimes(date,schedule,appointments,blocks,overrides)
    val dayBlocks=blocks.filter{blockDate(it)==date&&it.source=="manual_break"}
    Column(modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().height(38.dp)){
            Spacer(Modifier.width(labelW))
            Column(
                Modifier.weight(1f).fillMaxHeight().background(if(now)CAL_TODAY_BG else Color.Transparent,RoundedCornerShape(10.dp)).clickable{onEditAvailability(date,null)},
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center
            ){
                Text(date.format(DateTimeFormatter.ofPattern("EEEE",EC_RU)).replaceFirstChar{if(it.isLowerCase())it.titlecase(EC_RU) else it.toString()},style=MaterialTheme.typography.labelSmall,color=if(now)CAL_TODAY else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.format(DateTimeFormatter.ofPattern("d MMMM",EC_RU)),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=if(now)CAL_TODAY else MaterialTheme.colorScheme.onSurface)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)){
            val gridH=maxHeight
            val contentW=maxWidth-labelW
            Column(Modifier.fillMaxSize()){
                (START_HOUR until END_HOUR).forEach{h->
                    Row(Modifier.weight(1f).fillMaxWidth()){
                        Text(h.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(labelW).padding(top=2.dp))
                        Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(if(off)Color(0xFFF0ECEE) else if(now)CAL_TODAY_BG.copy(alpha=.45f) else CAL_GRID,RoundedCornerShape(4.dp)))
                    }
                }
            }
            frees.forEach{t->
                val p=LocalTime.parse(t);val m=p.hour*60+p.minute
                if(m in START_HOUR*60 until END_HOUR*60){
                    val y=gridH*((m-START_HOUR*60)/TOTAL_MINUTES.toFloat())
                    Surface(color=CAL_AVAILABLE,border=BorderStroke(.8.dp,CAL_AVAILABLE_BORDER.copy(alpha=.8f)),shape=RoundedCornerShape(5.dp),modifier=Modifier.offset(x=labelW,y=y).width(contentW).height(22.dp).padding(horizontal=1.dp).clickable{onEditAvailability(date,t)}){
                        Row(Modifier.fillMaxSize().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){Text(t,style=MaterialTheme.typography.labelMedium,color=Color(0xFF3E6C52),fontWeight=FontWeight.SemiBold)}
                    }
                }
            }
            dayBlocks.forEach{b->
                val sm=ecMinutes(b.startsAt)-START_HOUR*60;val dur=(ecMinutes(b.endsAt)-ecMinutes(b.startsAt)).coerceAtLeast(15)
                if(sm in 0 until TOTAL_MINUTES){
                    val y=gridH*(sm/TOTAL_MINUTES.toFloat());val hh=(gridH*(dur/TOTAL_MINUTES.toFloat())).coerceAtLeast(28.dp)
                    Surface(color=CAL_BREAK,shape=RoundedCornerShape(7.dp),modifier=Modifier.offset(x=labelW,y=y).width(contentW).height(hh).padding(horizontal=1.dp)){
                        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)){Text("${ecTime(b.startsAt).format(DateTimeFormatter.ofPattern("HH:mm"))}–${ecTime(b.endsAt).format(DateTimeFormatter.ofPattern("HH:mm"))} · Перерыв",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium);if(b.label.isNotBlank())Text(b.label,style=MaterialTheme.typography.labelSmall)}
                    }
                }
            }
            dayAppts.forEach{a->
                val sm=(ecMinutes(a.startsAt)-START_HOUR*60).coerceAtLeast(0);val dur=ecDuration(a).toInt()
                if(sm<TOTAL_MINUTES){
                    val y=gridH*(sm/TOTAL_MINUTES.toFloat());val hh=(gridH*(dur/TOTAL_MINUTES.toFloat())).coerceAtLeast(30.dp)
                    Surface(color=appointmentColor(a),shape=RoundedCornerShape(7.dp),tonalElevation=1.dp,modifier=Modifier.offset(x=labelW,y=y).width(contentW).height(hh).padding(horizontal=1.dp).clickable{onOpen(a)}){
                        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)){Text(ecTime(a.startsAt).format(DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,maxLines=1);Text(a.clientName,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);if(hh>44.dp){val s=ecServices(a);if(s.isNotBlank())Text(s,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)}}
                    }
                }
            }
        }
    }
}
'''
c = c[:start] + new_day + '\n'
cal.write_text(c, encoding='utf-8')

main = Path('app/src/main/java/ru/slotelly/app/NativeMainActivity.kt')
m = main.read_text(encoding='utf-8').replace('0.6.1 WORK','0.6.2 WORK')
main.write_text(m, encoding='utf-8')
