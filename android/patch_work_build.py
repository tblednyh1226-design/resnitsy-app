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
more.write_text(text, encoding='utf-8')

main = Path('app/src/main/java/ru/slotelly/app/NativeMainActivity.kt')
m = main.read_text(encoding='utf-8')
m = m.replace('Slotelly TEST','Slotelly').replace('Android 0.5.5 test','0.6 WORK').replace('Открыть Slotelly TEST','Открыть Slotelly')
main.write_text(m, encoding='utf-8')
