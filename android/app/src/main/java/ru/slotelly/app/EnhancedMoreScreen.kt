package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import ru.slotelly.app.data.NativeSettingsExtras
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val EM_BOOKING_LINK="https://tblednyh1226-design.github.io/resnitsy-app/booking.html"
private const val EM_BOT_LINK="https://t.me/resnicy_tatyana_bot"
private val EM_GSON=Gson()
private val EM_ZONE=ZoneId.of("Europe/Moscow")
private val EM_RU=Locale("ru","RU")

@Composable
fun EnhancedMoreScreen(pin:String,settingsJson:String,syncedAt:Long?,onSync:()->Unit,onSettingsChanged:()->Unit){
    val context=LocalContext.current
    val api=remember{NativeSettingsExtras()}
    val settings=remember(settingsJson){runCatching{JsonParser.parseString(settingsJson).asJsonObject}.getOrElse{JsonObject()}}
    val online=settings.getAsJsonObject("online_booking")?.get("enabled")?.asBoolean?:true
    var section by remember{mutableStateOf<String?>(null)}
    val ageText=syncedAt?.let{val sec=((System.currentTimeMillis()-it).coerceAtLeast(0)/1000);when{sec<60->"только что";sec<3600->"${sec/60} мин назад";else->"${sec/3600} ч назад"}}?:"ещё не синхронизировано"
    val schedule=settings.getAsJsonObject("schedule")
    val days=schedule?.getAsJsonArray("weekdays")?.joinToString(", "){dayName(it.asInt)}?:"—"
    val start=schedule?.get("start")?.asString?:"10:00"
    val end=schedule?.get("end")?.asString?:"22:30"

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Ещё",style=MaterialTheme.typography.headlineSmall)
        SettingsCard("Онлайн-запись",if(online)"Включена · ссылка для клиентов" else "Выключена · ссылка для клиентов"){section="online"}
        SettingsCard("Режим работы","$days · $start–$end"){section="work"}
        SettingsCard("Уведомления","Напоминания, каналы и шаблоны"){section="notifications"}
        SettingsCard("Техподдержка","Помощь и сообщение об ошибке"){section="support"}

        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp)){Text("Telegram-бот",fontWeight=FontWeight.SemiBold);Text("@resnicy_tatyana_bot");OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(EM_BOT_LINK)))}){Text("Открыть бота")}}
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp)){Text("Синхронизация",fontWeight=FontWeight.SemiBold);Text("Последняя: $ageText");OutlinedButton(onClick=onSync,modifier=Modifier.fillMaxWidth()){Text("Синхронизировать сейчас")}}
        }
    }

    when(section){
        "online"->OnlineBookingOverlay(pin,api,settings,onClose={section=null},onSaved={onSettingsChanged()})
        "work"->WorkSettingsOverlay(pin,api,settings,onClose={section=null},onSaved={onSettingsChanged();section=null})
        "notifications"->NotificationsOverlay(pin,api,onClose={section=null})
        "support"->SupportOverlay(onClose={section=null})
    }
}

@Composable private fun SettingsCard(title:String,sub:String,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable{onClick()}){Row(Modifier.padding(14.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(sub,style=MaterialTheme.typography.bodySmall)};Text("›",style=MaterialTheme.typography.headlineSmall)}}
}
private fun dayName(v:Int)=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").getOrElse(v){v.toString()}

@Composable
private fun OnlineBookingOverlay(pin:String,api:NativeSettingsExtras,settings:JsonObject,onClose:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var online by remember(settings){mutableStateOf(settings.getAsJsonObject("online_booking")?.get("enabled")?.asBoolean?:true)}
    var saving by remember{mutableStateOf(false)}
    var copied by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    FullOverlay(onClose){
        Text("Онлайн-запись",style=MaterialTheme.typography.headlineSmall)
        Card(colors=CardDefaults.cardColors(containerColor=if(online)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Онлайн-запись",fontWeight=FontWeight.SemiBold);Text(if(online)"Включена" else "Выключена",style=MaterialTheme.typography.bodySmall)}
                Switch(checked=online,onCheckedChange={v->online=v;saving=true;error="";scope.launch{runCatching{api.setOnlineBooking(pin,v)}.onSuccess{onSaved()}.onFailure{online=!v;error=it.message?:"Не удалось сохранить"};saving=false}},enabled=!saving)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Ссылка для клиентов",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("Эту ссылку можно отправлять клиентам для самостоятельной записи.",style=MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(EM_BOOKING_LINK,style=MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText("Slotelly",EM_BOOKING_LINK));copied=true},modifier=Modifier.weight(1f)){Text(if(copied)"Скопировано ✓" else "Копировать")}
                    OutlinedButton(onClick={val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Записаться онлайн: $EM_BOOKING_LINK")};context.startActivity(Intent.createChooser(i,"Поделиться"))},modifier=Modifier.weight(1f)){Text("Поделиться")}
                }
            }
        }
        if(!online)Text("Пока онлайн-запись выключена, клиентская форма не должна принимать новые записи.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WorkSettingsOverlay(pin:String,api:NativeSettingsExtras,settings:JsonObject,onClose:()->Unit,onSaved:()->Unit){
    val scope=rememberCoroutineScope();val schedule=settings.getAsJsonObject("schedule")?:JsonObject()
    var start by remember{mutableStateOf(schedule.get("start")?.asString?:"10:00")};var end by remember{mutableStateOf(schedule.get("end")?.asString?:"22:30")}
    var weekdays by remember{mutableStateOf(schedule.getAsJsonArray("weekdays")?.map{it.asInt}?.toSet()?:emptySet())};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val fullDays=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")
    FullOverlay(onClose){
        Text("Режим работы",style=MaterialTheme.typography.headlineSmall)
        Text("Включено = рабочий день, выключено = выходной",style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            fullDays.forEachIndexed{d,label->
                val working=d in weekdays
                Card(colors=CardDefaults.cardColors(containerColor=if(working) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(label,fontWeight=if(working) FontWeight.SemiBold else FontWeight.Normal);Text(if(working)"Рабочий" else "Выходной",style=MaterialTheme.typography.bodySmall,color=if(working)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                        Switch(checked=working,onCheckedChange={on->weekdays=if(on) weekdays+d else weekdays-d})
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Границы рабочего дня",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(start,{start=it},label={Text("Начало")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(end,{end=it},label={Text("Конец")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text("Разовые выходные и ручные изменения окошек сохраняются отдельно и не стираются этой формой.",style=MaterialTheme.typography.bodySmall)
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick={saving=true;scope.launch{val base=runCatching{EM_GSON.fromJson(schedule,MutableMap::class.java) as MutableMap<String,Any?>}.getOrElse{mutableMapOf()};base["start"]=start;base["end"]=end;base["weekdays"]=weekdays.sorted();runCatching{api.patchSettings(pin,mapOf("schedule" to base))}.onSuccess{onSaved()}.onFailure{error=it.message?:"Ошибка сохранения"};saving=false}},enabled=!saving&&weekdays.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(if(saving)"Сохраняю…" else "Сохранить режим работы")}
    }
}

@Composable
private fun NotificationsOverlay(pin:String,api:NativeSettingsExtras,onClose:()->Unit){
    var data by remember{mutableStateOf<JsonObject?>(null)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var showHistory by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){runCatching{api.notificationsGet(pin)}.onSuccess{data=it}.onFailure{error=it.message?:"Не удалось загрузить"};loading=false}
    FullOverlay(onClose){
        Text("Уведомления",style=MaterialTheme.typography.headlineSmall)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        data?.let{root->NotificationSettingsBody(pin,api,root,onOpenHistory={showHistory=true})}
    }
    if(showHistory) NotificationHistoryOverlay(pin,api,onClose={showHistory=false})
}

@Composable
private fun ColumnScope.NotificationSettingsBody(pin:String,api:NativeSettingsExtras,root:JsonObject,onOpenHistory:()->Unit){
    val scope=rememberCoroutineScope();val n=root.getAsJsonObject("settings")?:JsonObject();val status=root.getAsJsonObject("status")?:JsonObject();val channels=n.getAsJsonObject("channels")?:JsonObject();val master=n.getAsJsonObject("master_push")?:JsonObject();val templates=n.getAsJsonObject("templates")?:JsonObject()
    var enabled by remember{mutableStateOf(n.get("enabled")?.asBoolean?:true)};var confirm by remember{mutableStateOf(n.get("confirmation_enabled")?.asBoolean?:true)};var move by remember{mutableStateOf(n.get("reschedule_enabled")?.asBoolean?:true)};var cancel by remember{mutableStateOf(n.get("cancellation_enabled")?.asBoolean?:true)}
    var dayBefore by remember{mutableStateOf(n.get("reminder_day_before")?.asBoolean?:true)};var dayTime by remember{mutableStateOf(n.get("reminder_day_before_time")?.asString?:"19:00")};var hoursOn by remember{mutableStateOf(n.get("reminder_hours_before")?.asBoolean?:false)};var hours by remember{mutableStateOf(n.get("reminder_hours")?.asInt?.toString()?:"3")}
    var tg by remember{mutableStateOf(channels.get("telegram")?.asBoolean?:false)};var wa by remember{mutableStateOf(channels.get("whatsapp")?.asBoolean?:false)};var vk by remember{mutableStateOf(channels.get("vk")?.asBoolean?:false)};var max by remember{mutableStateOf(channels.get("max")?.asBoolean?:false)}
    var tplConfirm by remember{mutableStateOf(templates.get("confirmation")?.asString?:"")};var tplReminder by remember{mutableStateOf(templates.get("reminder")?.asString?:"")};var tplMove by remember{mutableStateOf(templates.get("reschedule")?.asString?:"")};var tplCancel by remember{mutableStateOf(templates.get("cancellation")?.asString?:"")};var saveMsg by remember{mutableStateOf("")}
    val scroll=rememberScrollState()
    Column(Modifier.weight(1f).verticalScroll(scroll)){
        Card(Modifier.fillMaxWidth().clickable{onOpenHistory()}){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("История отправки",fontWeight=FontWeight.SemiBold);Text("Текущая и прошлые недели",style=MaterialTheme.typography.bodySmall)};Text("›",style=MaterialTheme.typography.headlineSmall)}}
        Spacer(Modifier.height(8.dp))
        val bot=status.getAsJsonObject("telegram_bot");Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("Telegram-бот",fontWeight=FontWeight.SemiBold);Text(if(bot!=null)"Подключён @${bot.get("bot_username")?.asString?:""}" else "Не подключён",style=MaterialTheme.typography.bodySmall)}}
        NotifySwitch("Автоуведомления клиентам",enabled){enabled=it};NotifySwitch("Подтверждение записи",confirm){confirm=it};NotifySwitch("Перенос записи",move){move=it};NotifySwitch("Отмена записи",cancel){cancel=it};NotifySwitch("Напоминание накануне",dayBefore){dayBefore=it}
        OutlinedTextField(dayTime,{dayTime=it},label={Text("Время напоминания")},modifier=Modifier.fillMaxWidth());NotifySwitch("Доп. напоминание за несколько часов",hoursOn){hoursOn=it};OutlinedTextField(hours,{hours=it},label={Text("За сколько часов")},modifier=Modifier.fillMaxWidth())
        Text("Каналы",style=MaterialTheme.typography.titleMedium);NotifySwitch("Telegram",tg){tg=it};NotifySwitch("WhatsApp",wa){wa=it};NotifySwitch("VK",vk){vk=it};NotifySwitch("MAX",max){max=it}
        Text("Шаблоны",style=MaterialTheme.typography.titleMedium);OutlinedTextField(tplConfirm,{tplConfirm=it},label={Text("Подтверждение")},minLines=3,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplReminder,{tplReminder=it},label={Text("Напоминание")},minLines=3,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplMove,{tplMove=it},label={Text("Перенос")},minLines=2,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplCancel,{tplCancel=it},label={Text("Отмена")},minLines=2,modifier=Modifier.fillMaxWidth())
    }
    if(saveMsg.isNotBlank())Text(saveMsg,style=MaterialTheme.typography.bodySmall)
    Button(onClick={scope.launch{val settings=mapOf<String,Any?>("enabled" to enabled,"confirmation_enabled" to confirm,"reschedule_enabled" to move,"cancellation_enabled" to cancel,"reminder_day_before" to dayBefore,"reminder_day_before_time" to dayTime,"reminder_hours_before" to hoursOn,"reminder_hours" to (hours.toIntOrNull()?:3),"channels" to mapOf("telegram" to tg,"whatsapp" to wa,"vk" to vk,"max" to max),"master_push" to EM_GSON.fromJson(master,Map::class.java),"templates" to mapOf("confirmation" to tplConfirm,"reminder" to tplReminder,"reschedule" to tplMove,"cancellation" to tplCancel));runCatching{api.notificationsSave(pin,settings)}.onSuccess{saveMsg="Сохранено ✓"}.onFailure{saveMsg=it.message?:"Ошибка"}}},modifier=Modifier.fillMaxWidth()){Text("Сохранить уведомления")}
}

@Composable
private fun NotificationHistoryOverlay(pin:String,api:NativeSettingsExtras,onClose:()->Unit){
    var rows by remember{mutableStateOf<List<JsonObject>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var weekOffset by remember{mutableStateOf(0)}
    LaunchedEffect(Unit){runCatching{api.notificationsJournal(pin,limit=500)}.onSuccess{j->rows=j.getAsJsonArray("rows")?.map{it.asJsonObject}?:emptyList()}.onFailure{error=it.message?:"Не удалось загрузить историю"};loading=false}
    val currentMonday=LocalDate.now(EM_ZONE).minusDays((LocalDate.now(EM_ZONE).dayOfWeek.value-1).toLong())
    val monday=currentMonday.plusWeeks(weekOffset.toLong())
    val sunday=monday.plusDays(6)
    val weekRows=rows.filter{r->journalDate(r)?.let{!it.isBefore(monday)&&!it.isAfter(sunday)}==true}
    val sent=weekRows.count{statusOk(it)}
    val failed=weekRows.count{statusFailed(it)}
    var drag by remember{mutableStateOf(0f)}

    FullOverlay(onClose){
        Text("История отправки",style=MaterialTheme.typography.headlineSmall)
        Text("По неделям",style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={weekOffset--}){Text("‹",style=MaterialTheme.typography.headlineSmall)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                Text("${monday.format(DateTimeFormatter.ofPattern("d MMM",EM_RU))} – ${sunday.format(DateTimeFormatter.ofPattern("d MMM",EM_RU))}",fontWeight=FontWeight.SemiBold)
                Text(if(weekOffset==0)"Текущая неделя" else if(weekOffset==-1)"Прошлая неделя" else "${-weekOffset} нед. назад",style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={if(weekOffset<0){{weekOffset++}}else null},enabled=weekOffset<0){Text("›",style=MaterialTheme.typography.headlineSmall)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SummaryCard("Всего",weekRows.size,Modifier.weight(1f))
            SummaryCard("Отправлено",sent,Modifier.weight(1f))
            SummaryCard("Ошибки",failed,Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f).fillMaxWidth().pointerInput(weekOffset){detectHorizontalDragGestures(onDragEnd={if(drag<-60f&&weekOffset<0)weekOffset++ else if(drag>60f)weekOffset--;drag=0f}){_,amount->drag+=amount}}.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(!loading&&weekRows.isEmpty()) Text("За эту неделю уведомлений не было",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            weekRows.sortedByDescending{journalInstant(it)}.forEach{r->
                val client=notificationClient(r)
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(client,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
                                Text(notificationType(r),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(statusLabel(r),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=if(statusFailed(r))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        val whenText=journalInstant(r)?.atZone(EM_ZONE)?.format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm",EM_RU))
                        if(whenText!=null)Text(whenText.replace(".",""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        val message=r.get("message_text")?.takeUnless{it.isJsonNull}?.asString.orEmpty()
                        if(message.isNotBlank())Text(message,maxLines=3,style=MaterialTheme.typography.bodySmall)
                        val channel=channelLabel(r)
                        if(channel.isNotBlank())Text(channel,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable private fun SummaryCard(title:String,value:Int,modifier:Modifier){Card(modifier){Column(Modifier.padding(9.dp)){Text(value.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(title,style=MaterialTheme.typography.labelSmall)}}}
private fun journalInstant(r:JsonObject):Instant?{for(k in listOf("sent_at","created_at","scheduled_at","updated_at")){val s=r.get(k)?.takeUnless{it.isJsonNull}?.asString?:continue;runCatching{return Instant.parse(s)};runCatching{return OffsetDateTime.parse(s).toInstant()};runCatching{return LocalDateTime.parse(s).atZone(EM_ZONE).toInstant()}};return null}
private fun journalDate(r:JsonObject):LocalDate?=journalInstant(r)?.atZone(EM_ZONE)?.toLocalDate()
private fun journalStatus(r:JsonObject)=r.get("delivery_status")?.takeUnless{it.isJsonNull}?.asString?.lowercase()?:r.get("status")?.takeUnless{it.isJsonNull}?.asString?.lowercase().orEmpty()
private fun statusFailed(r:JsonObject)=journalStatus(r) in setOf("failed","error","rejected","blocked","undelivered")
private fun statusOk(r:JsonObject)=journalStatus(r) in setOf("sent","delivered","read","success","ok")
private fun statusLabel(r:JsonObject):String=when(journalStatus(r)){
    "delivered"->"Доставлено"
    "read"->"Прочитано"
    "sent","success","ok"->"Отправлено"
    "queued","pending","scheduled","ready","processing"->"Ожидает отправки"
    "failed","error","rejected","blocked","undelivered"->"Ошибка отправки"
    "cancelled","canceled"->"Отменено"
    else->"Статус неизвестен"
}
private fun notificationType(r:JsonObject):String=when((r.get("template_type")?:r.get("type"))?.takeUnless{it.isJsonNull}?.asString?.lowercase()){
    "confirmation","confirm","booking_confirmation"->"Подтверждение записи"
    "reminder","appointment_reminder"->"Напоминание о записи"
    "reschedule","rescheduled","move"->"Перенос записи"
    "cancellation","cancel","cancelled","canceled"->"Отмена записи"
    "payment","payment_reminder"->"Напоминание об оплате"
    else->"Уведомление клиенту"
}
private fun notificationClient(r:JsonObject):String{
    for(k in listOf("client_name","client_display_name","display_name","recipient_name","customer_name","name")){
        val v=r.get(k)?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty()
        if(v.isNotBlank())return v
    }
    val phone=for(k in listOf("recipient_phone","phone","client_phone")){
        val v=r.get(k)?.takeUnless{it.isJsonNull}?.asString?.trim().orEmpty();if(v.isNotBlank())break v
    }
    return if(phone.isNotBlank())"Клиент · $phone" else "Клиент"
}
private fun channelLabel(r:JsonObject):String=when(r.get("channel")?.takeUnless{it.isJsonNull}?.asString?.lowercase()){
    "telegram","tg"->"Telegram"
    "whatsapp","wa"->"WhatsApp"
    "vk","vkontakte"->"VK"
    "max"->"MAX"
    "sms"->"SMS"
    "push"->"Push-уведомление"
    else->r.get("channel")?.takeUnless{it.isJsonNull}?.asString.orEmpty()
}

@Composable private fun NotifySwitch(title:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(title,modifier=Modifier.weight(1f));Switch(value,onCheckedChange=onChange)}}

@Composable private fun SupportOverlay(onClose:()->Unit){FullOverlay(onClose){Text("Техподдержка",style=MaterialTheme.typography.headlineSmall);Text("Раздел сохранён как в веб-версии. Здесь будут обращения и их статусы.");Spacer(Modifier.height(12.dp));Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Написать в поддержку",fontWeight=FontWeight.SemiBold);Text("Вопрос или помощь по работе приложения",style=MaterialTheme.typography.bodySmall)}};Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Сообщить об ошибке",fontWeight=FontWeight.SemiBold);Text("Описание и скриншот",style=MaterialTheme.typography.bodySmall)}};Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Мои обращения",fontWeight=FontWeight.SemiBold);Text("История и статус обращений",style=MaterialTheme.typography.bodySmall)}}}}
