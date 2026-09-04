from pathlib import Path

more = Path('app/src/main/java/ru/slotelly/app/EnhancedMoreScreen.kt')
text = more.read_text(encoding='utf-8')
start = text.index('@Composable\nprivate fun OnlineBookingOverlay')
end = text.index('\n\n@Composable\nprivate fun WorkSettingsOverlay', start)
new_block = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineBookingOverlay(pin:String,api:NativeSettingsExtras,settings:JsonObject,onClose:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val today=LocalDate.now(EM_ZONE)
    val booking=settings.getAsJsonObject("online_booking")?:JsonObject()
    var online by remember(settings){mutableStateOf(booking.get("enabled")?.asBoolean?:true)}
    var mode by remember(settings){mutableStateOf(booking.get("booking_depth_mode")?.asString?:"days_30")}
    var customDate by remember(settings){mutableStateOf(runCatching{LocalDate.parse(booking.get("booking_until")?.asString?:"")}.getOrNull()?:today.plusDays(30))}
    var saving by remember{mutableStateOf(false)};var copied by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var savedMsg by remember{mutableStateOf("")};var showPicker by remember{mutableStateOf(false)}
    fun limitDate():LocalDate=when(mode){"current_month"->today.withDayOfMonth(today.lengthOfMonth());"days_60"->today.plusDays(60);"until_date"->customDate;else->today.plusDays(30)}
    fun saveDepth(){saving=true;error="";savedMsg="";scope.launch{val until=limitDate().toString();val patch=mapOf("online_booking" to mapOf("enabled" to online,"booking_depth_mode" to mode,"booking_until" to until));runCatching{api.patchSettings(pin,patch)}.onSuccess{savedMsg="Сохранено ✓";onSaved()}.onFailure{error=it.message?:"Не удалось сохранить"};saving=false}}
    FullOverlay(onClose){
        Text("Онлайн-запись",style=MaterialTheme.typography.headlineSmall)
        Card(colors=CardDefaults.cardColors(containerColor=if(online)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Онлайн-запись",fontWeight=FontWeight.SemiBold);Text(if(online)"Включена" else "Выключена",style=MaterialTheme.typography.bodySmall)};Switch(checked=online,onCheckedChange={v->online=v;saving=true;error="";scope.launch{runCatching{api.setOnlineBooking(pin,v)}.onSuccess{onSaved()}.onFailure{online=!v;error=it.message?:"Не удалось сохранить"};saving=false}},enabled=!saving)}
        }
        Spacer(Modifier.height(10.dp));Text("Глубина записи",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("На какой период вперёд клиент сможет выбрать дату.",style=MaterialTheme.typography.bodySmall)
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            listOf("current_month" to "До конца текущего месяца","days_30" to "На 30 дней","days_60" to "На 60 дней","until_date" to "До выбранной даты").forEach{(key,label)->
                Card(Modifier.fillMaxWidth().clickable{mode=key}){Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=mode==key,onClick={mode=key});Text(label,modifier=Modifier.weight(1f))}}
            }
        }
        if(mode=="until_date") OutlinedButton(onClick={showPicker=true},modifier=Modifier.fillMaxWidth()){Text("Выбрать дату · ${customDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy",EM_RU))}")}
        Text("Запись будет доступна по ${limitDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy",EM_RU))} включительно.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={saveDepth()},enabled=!saving,modifier=Modifier.fillMaxWidth()){Text(if(saving)"Сохраняю…" else "Сохранить глубину записи")}
        if(savedMsg.isNotBlank())Text(savedMsg,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp));Text("Ссылка для клиентов",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("Эту ссылку можно отправлять клиентам для самостоятельной записи.",style=MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(EM_BOOKING_LINK,style=MaterialTheme.typography.bodyMedium);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText("Slotelly",EM_BOOKING_LINK));copied=true},modifier=Modifier.weight(1f)){Text(if(copied)"Скопировано ✓" else "Копировать")};OutlinedButton(onClick={val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Записаться онлайн: $EM_BOOKING_LINK")};context.startActivity(Intent.createChooser(i,"Поделиться"))},modifier=Modifier.weight(1f)){Text("Поделиться")}}}}
        if(!online)Text("Пока онлайн-запись выключена, клиентская форма не должна принимать новые записи.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    if(showPicker){
        val initial=customDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={showPicker=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let{customDate=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()};showPicker=false}){Text("Выбрать")}},dismissButton={TextButton(onClick={showPicker=false}){Text("Отмена")}}){DatePicker(state=picker)}
    }
}'''
text = text[:start] + new_block + text[end:]

ws = text.index('@Composable\nprivate fun WorkSettingsOverlay')
we = text.index('\n\n@Composable\nprivate fun NotificationsOverlay', ws)
compact_work = r'''@Composable
private fun WorkSettingsOverlay(pin:String,api:NativeSettingsExtras,settings:JsonObject,onClose:()->Unit,onSaved:()->Unit){
    val scope=rememberCoroutineScope();val schedule=settings.getAsJsonObject("schedule")?:JsonObject()
    var start by remember{mutableStateOf(schedule.get("start")?.asString?:"10:00")};var end by remember{mutableStateOf(schedule.get("end")?.asString?:"22:30")};var weekdays by remember{mutableStateOf(schedule.getAsJsonArray("weekdays")?.map{it.asInt}?.toSet()?:emptySet())};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val fullDays=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")
    FullOverlay(onClose){
        Text("Режим работы",style=MaterialTheme.typography.headlineSmall)
        Text("Включено = рабочий день · выключено = выходной",style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            fullDays.forEachIndexed{d,label->val working=d in weekdays
                Card(colors=CardDefaults.cardColors(containerColor=if(working)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)),modifier=Modifier.fillMaxWidth().weight(1f)){
                    Row(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.bodyLarge,fontWeight=if(working)FontWeight.SemiBold else FontWeight.Normal);Text(if(working)"Рабочий" else "Выходной",style=MaterialTheme.typography.labelSmall,color=if(working)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                        Switch(checked=working,onCheckedChange={on->weekdays=if(on)weekdays+d else weekdays-d})
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp));Text("Рабочий день",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},label={Text("Начало")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(end,{end=it},label={Text("Конец")},singleLine=true,modifier=Modifier.weight(1f))}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp));Button(onClick={saving=true;scope.launch{val base=runCatching{EM_GSON.fromJson(schedule,MutableMap::class.java) as MutableMap<String,Any?>}.getOrElse{mutableMapOf()};base["start"]=start;base["end"]=end;base["weekdays"]=weekdays.sorted();runCatching{api.patchSettings(pin,mapOf("schedule" to base))}.onSuccess{onSaved()}.onFailure{error=it.message?:"Ошибка сохранения"};saving=false}},enabled=!saving&&weekdays.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(if(saving)"Сохраняю…" else "Сохранить режим работы")}
    }
}'''
text = text[:ws] + compact_work + text[we:]
more.write_text(text, encoding='utf-8')

main = Path('app/src/main/java/ru/slotelly/app/NativeMainActivity.kt')
m = main.read_text(encoding='utf-8')
m = m.replace('Slotelly TEST','Slotelly').replace('Android 0.5.5 test','0.6.1 WORK').replace('Открыть Slotelly TEST','Открыть Slotelly')
main.write_text(m, encoding='utf-8')

cal = Path('app/src/main/java/ru/slotelly/app/EnhancedCalendar.kt')
c = cal.read_text(encoding='utf-8')
c = c.replace('import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n')
c = c.replace('import androidx.compose.ui.Modifier\n','import androidx.compose.ui.Modifier\nimport androidx.compose.ui.input.pointer.pointerInput\n')
old = '    Column(Modifier.fillMaxSize().padding(horizontal=10.dp)) {'
new = '''    var swipeX by remember { mutableStateOf(0f) }\n    Column(Modifier.fillMaxSize().padding(horizontal=10.dp).pointerInput(mode,focus){\n        detectHorizontalDragGestures(\n            onDragEnd={\n                if(swipeX < -70f) focus=date.plusDays(if(mode==CalendarMode.WEEK)7 else 1).toString()\n                else if(swipeX > 70f) focus=date.minusDays(if(mode==CalendarMode.WEEK)7 else 1).toString()\n                swipeX=0f\n            },\n            onHorizontalDrag={_,amount->swipeX+=amount}\n        )\n    }) {'''
if old not in c: raise SystemExit('calendar root marker not found')
c = c.replace(old,new,1)
cal.write_text(c, encoding='utf-8')
