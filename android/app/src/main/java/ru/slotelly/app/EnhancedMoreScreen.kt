package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import ru.slotelly.app.data.NativeSettingsExtras

private const val EM_BOOKING_LINK="https://tblednyh1226-design.github.io/resnitsy-app/booking.html"
private const val EM_BOT_LINK="https://t.me/resnicy_tatyana_bot"
private val EM_GSON=Gson()

@Composable
fun EnhancedMoreScreen(pin:String,settingsJson:String,syncedAt:Long?,onSync:()->Unit,onSettingsChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val api=remember{NativeSettingsExtras()}
    val settings=remember(settingsJson){runCatching{JsonParser.parseString(settingsJson).asJsonObject}.getOrElse{JsonObject()}}
    var online by remember(settingsJson){mutableStateOf(settings.getAsJsonObject("online_booking")?.get("enabled")?.asBoolean?:true)}
    var section by remember{mutableStateOf<String?>(null)}
    var copied by remember{mutableStateOf(false)}
    var savingOnline by remember{mutableStateOf(false)}
    val ageText=syncedAt?.let{val sec=((System.currentTimeMillis()-it).coerceAtLeast(0)/1000);when{sec<60->"только что";sec<3600->"${sec/60} мин назад";else->"${sec/3600} ч назад"}}?:"ещё не синхронизировано"
    val schedule=settings.getAsJsonObject("schedule")
    val days=schedule?.getAsJsonArray("weekdays")?.joinToString(", "){dayName(it.asInt)}?:"—"
    val start=schedule?.get("start")?.asString?:"10:00"
    val end=schedule?.get("end")?.asString?:"22:30"

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Ещё",style=MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()){
            Row(Modifier.padding(14.dp).fillMaxWidth()){
                Column(Modifier.weight(1f)){Text("Онлайн-запись",fontWeight=FontWeight.SemiBold);Text(if(online)"Включена" else "Выключена",style=MaterialTheme.typography.bodySmall)}
                Switch(checked=online,onCheckedChange={v->
                    online=v;savingOnline=true
                    scope.launch{runCatching{api.setOnlineBooking(pin,v)}.onSuccess{onSettingsChanged()};savingOnline=false}
                },enabled=!savingOnline)
            }
        }
        SettingsCard("Режим работы","$days · $start–$end"){section="work"}
        SettingsCard("Уведомления","Напоминания, каналы, шаблоны и журнал"){section="notifications"}
        SettingsCard("Техподдержка","Помощь и сообщение об ошибке"){section="support"}

        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("Ссылка для клиентов",fontWeight=FontWeight.SemiBold);Text(EM_BOOKING_LINK,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText("Slotelly",EM_BOOKING_LINK));copied=true},modifier=Modifier.weight(1f)){Text(if(copied)"Скопировано ✓" else "Копировать")}
                    OutlinedButton(onClick={val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Записаться онлайн: $EM_BOOKING_LINK")};context.startActivity(Intent.createChooser(i,"Поделиться"))},modifier=Modifier.weight(1f)){Text("Поделиться")}
                }
            }
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp)){Text("Telegram-бот",fontWeight=FontWeight.SemiBold);Text("@resnicy_tatyana_bot");OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(EM_BOT_LINK)))}){Text("Открыть бота")}}
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp)){Text("Синхронизация",fontWeight=FontWeight.SemiBold);Text("Последняя: $ageText");OutlinedButton(onClick=onSync,modifier=Modifier.fillMaxWidth()){Text("Синхронизировать сейчас")}}
        }
    }

    when(section){
        "work"->WorkSettingsOverlay(pin,api,settings,onClose={section=null},onSaved={onSettingsChanged();section=null})
        "notifications"->NotificationsOverlay(pin,api,onClose={section=null})
        "support"->SupportOverlay(onClose={section=null})
    }
}

@Composable private fun SettingsCard(title:String,sub:String,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable{onClick()}){Row(Modifier.padding(14.dp).fillMaxWidth()){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(sub,style=MaterialTheme.typography.bodySmall)};Text("›",style=MaterialTheme.typography.headlineSmall)}}
}
private fun dayName(v:Int)=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").getOrElse(v){v.toString()}

@Composable
private fun WorkSettingsOverlay(pin:String,api:NativeSettingsExtras,settings:JsonObject,onClose:()->Unit,onSaved:()->Unit){
    val scope=rememberCoroutineScope();val schedule=settings.getAsJsonObject("schedule")?:JsonObject()
    var start by remember{mutableStateOf(schedule.get("start")?.asString?:"10:00")};var end by remember{mutableStateOf(schedule.get("end")?.asString?:"22:30")}
    var weekdays by remember{mutableStateOf(schedule.getAsJsonArray("weekdays")?.map{it.asInt}?.toSet()?:emptySet())};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val fullDays=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")
    FullOverlay(onClose){
        Text("Режим работы",style=MaterialTheme.typography.headlineSmall)
        Text("Выберите базовые рабочие дни",style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            fullDays.forEachIndexed{d,label->
                Card(colors=CardDefaults.cardColors(containerColor=if(d in weekdays) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(label,modifier=Modifier.weight(1f),fontWeight=if(d in weekdays) FontWeight.SemiBold else FontWeight.Normal)
                        Switch(checked=d in weekdays,onCheckedChange={on->weekdays=if(on) weekdays+d else weekdays-d})
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
        Button(onClick={saving=true;scope.launch{
            val base=runCatching{EM_GSON.fromJson(schedule,MutableMap::class.java) as MutableMap<String,Any?>}.getOrElse{mutableMapOf()};base["start"]=start;base["end"]=end;base["weekdays"]=weekdays.sorted()
            runCatching{api.patchSettings(pin,mapOf("schedule" to base))}.onSuccess{onSaved()}.onFailure{error=it.message?:"Ошибка сохранения"};saving=false
        }},enabled=!saving&&weekdays.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(if(saving)"Сохраняю…" else "Сохранить режим работы")}
    }
}

@Composable
private fun NotificationsOverlay(pin:String,api:NativeSettingsExtras,onClose:()->Unit){
    val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JsonObject?>(null)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var journal by remember{mutableStateOf<List<JsonObject>>(emptyList())}
    LaunchedEffect(Unit){runCatching{api.notificationsGet(pin)}.onSuccess{data=it}.onFailure{error=it.message?:"Не удалось загрузить"};runCatching{api.notificationsJournal(pin)}.onSuccess{j->journal=j.getAsJsonArray("rows")?.map{it.asJsonObject}?:emptyList()};loading=false}
    FullOverlay(onClose){
        Text("Уведомления",style=MaterialTheme.typography.headlineSmall);if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        data?.let{root->NotificationSettingsBody(pin,api,root,journal)}
    }
}

@Composable
private fun ColumnScope.NotificationSettingsBody(pin:String,api:NativeSettingsExtras,root:JsonObject,journal:List<JsonObject>){
    val scope=rememberCoroutineScope();val n=root.getAsJsonObject("settings")?:JsonObject();val status=root.getAsJsonObject("status")?:JsonObject();val channels=n.getAsJsonObject("channels")?:JsonObject();val master=n.getAsJsonObject("master_push")?:JsonObject();val templates=n.getAsJsonObject("templates")?:JsonObject()
    var enabled by remember{mutableStateOf(n.get("enabled")?.asBoolean?:true)};var confirm by remember{mutableStateOf(n.get("confirmation_enabled")?.asBoolean?:true)};var move by remember{mutableStateOf(n.get("reschedule_enabled")?.asBoolean?:true)};var cancel by remember{mutableStateOf(n.get("cancellation_enabled")?.asBoolean?:true)}
    var dayBefore by remember{mutableStateOf(n.get("reminder_day_before")?.asBoolean?:true)};var dayTime by remember{mutableStateOf(n.get("reminder_day_before_time")?.asString?:"19:00")};var hoursOn by remember{mutableStateOf(n.get("reminder_hours_before")?.asBoolean?:false)};var hours by remember{mutableStateOf(n.get("reminder_hours")?.asInt?.toString()?:"3")}
    var tg by remember{mutableStateOf(channels.get("telegram")?.asBoolean?:false)};var wa by remember{mutableStateOf(channels.get("whatsapp")?.asBoolean?:false)};var vk by remember{mutableStateOf(channels.get("vk")?.asBoolean?:false)};var max by remember{mutableStateOf(channels.get("max")?.asBoolean?:false)}
    var tplConfirm by remember{mutableStateOf(templates.get("confirmation")?.asString?:"")};var tplReminder by remember{mutableStateOf(templates.get("reminder")?.asString?:"")};var tplMove by remember{mutableStateOf(templates.get("reschedule")?.asString?:"")};var tplCancel by remember{mutableStateOf(templates.get("cancellation")?.asString?:"")};var saveMsg by remember{mutableStateOf("")}
    val scroll=rememberScrollState()
    Column(Modifier.weight(1f).verticalScroll(scroll)){
        val bot=status.getAsJsonObject("telegram_bot");Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("Telegram-бот",fontWeight=FontWeight.SemiBold);Text(if(bot!=null)"Подключён @${bot.get("bot_username")?.asString?:""}" else "Не подключён",style=MaterialTheme.typography.bodySmall)}}
        NotifySwitch("Автоуведомления клиентам",enabled){enabled=it};NotifySwitch("Подтверждение записи",confirm){confirm=it};NotifySwitch("Перенос записи",move){move=it};NotifySwitch("Отмена записи",cancel){cancel=it};NotifySwitch("Напоминание накануне",dayBefore){dayBefore=it}
        OutlinedTextField(dayTime,{dayTime=it},label={Text("Время напоминания")},modifier=Modifier.fillMaxWidth());NotifySwitch("Доп. напоминание за несколько часов",hoursOn){hoursOn=it};OutlinedTextField(hours,{hours=it},label={Text("За сколько часов")},modifier=Modifier.fillMaxWidth())
        Text("Каналы",style=MaterialTheme.typography.titleMedium);NotifySwitch("Telegram",tg){tg=it};NotifySwitch("WhatsApp",wa){wa=it};NotifySwitch("VK",vk){vk=it};NotifySwitch("MAX",max){max=it}
        Text("Шаблоны",style=MaterialTheme.typography.titleMedium);OutlinedTextField(tplConfirm,{tplConfirm=it},label={Text("Подтверждение")},minLines=3,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplReminder,{tplReminder=it},label={Text("Напоминание")},minLines=3,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplMove,{tplMove=it},label={Text("Перенос")},minLines=2,modifier=Modifier.fillMaxWidth());OutlinedTextField(tplCancel,{tplCancel=it},label={Text("Отмена")},minLines=2,modifier=Modifier.fillMaxWidth())
        Text("Журнал уведомлений",style=MaterialTheme.typography.titleMedium);journal.take(12).forEach{r->Card(Modifier.fillMaxWidth().padding(vertical=2.dp)){Column(Modifier.padding(8.dp)){Text("${r.get("template_type")?.asString?:"Сообщение"} · ${r.get("delivery_status")?.asString?:""}",fontWeight=FontWeight.SemiBold);Text(r.get("message_text")?.asString?:"",maxLines=2,style=MaterialTheme.typography.bodySmall)}}}
    }
    if(saveMsg.isNotBlank())Text(saveMsg,style=MaterialTheme.typography.bodySmall)
    Button(onClick={scope.launch{
        val settings=mapOf<String,Any?>("enabled" to enabled,"confirmation_enabled" to confirm,"reschedule_enabled" to move,"cancellation_enabled" to cancel,"reminder_day_before" to dayBefore,"reminder_day_before_time" to dayTime,"reminder_hours_before" to hoursOn,"reminder_hours" to (hours.toIntOrNull()?:3),"channels" to mapOf("telegram" to tg,"whatsapp" to wa,"vk" to vk,"max" to max),"master_push" to EM_GSON.fromJson(master,Map::class.java),"templates" to mapOf("confirmation" to tplConfirm,"reminder" to tplReminder,"reschedule" to tplMove,"cancellation" to tplCancel))
        runCatching{api.notificationsSave(pin,settings)}.onSuccess{saveMsg="Сохранено ✓"}.onFailure{saveMsg=it.message?:"Ошибка"}
    }},modifier=Modifier.fillMaxWidth()){Text("Сохранить уведомления")}
}

@Composable private fun NotifySwitch(title:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(title,modifier=Modifier.weight(1f));Switch(value,onCheckedChange=onChange)}}

@Composable private fun SupportOverlay(onClose:()->Unit){FullOverlay(onClose){Text("Техподдержка",style=MaterialTheme.typography.headlineSmall);Text("Раздел сохранён как в веб-версии. Здесь будут обращения и их статусы.");Spacer(Modifier.height(12.dp));Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Написать в поддержку",fontWeight=FontWeight.SemiBold);Text("Вопрос или помощь по работе приложения",style=MaterialTheme.typography.bodySmall)}};Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Сообщить об ошибке",fontWeight=FontWeight.SemiBold);Text("Описание и скриншот",style=MaterialTheme.typography.bodySmall)}};Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Мои обращения",fontWeight=FontWeight.SemiBold);Text("История и статус обращений",style=MaterialTheme.typography.bodySmall)}}}}
