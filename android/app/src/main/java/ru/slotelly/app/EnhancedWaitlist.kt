package ru.slotelly.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.slotelly.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EWL_ZONE=ZoneId.of("Europe/Moscow")
private val EWL_RU=Locale("ru","RU")
private fun ewlDt(iso:String?):String=if(iso.isNullOrBlank())"—" else runCatching{
    Instant.parse(iso).atZone(EWL_ZONE).format(DateTimeFormatter.ofPattern("dd.MM (EEE) в HH:mm",EWL_RU)).replace(".","")
}.getOrDefault(iso)
private fun ewlStatus(s:String)=when(s){"active"->"Ждёт";"offered"->"Предложено";"accepted"->"Согласовано";"expired"->"Истёк";"cancelled"->"Отменено";else->s}
private fun ewlEvent(s:String)=when(s){"created"->"Запрос создан";"updated"->"Изменено";"offered"->"Предложено окно";"declined"->"Не подошло";"accepted"->"Согласовано";"expired"->"Истёк срок";"session_passed"->"Запись прошла";"cancelled"->"Отменено";"reactivated"->"Снова активен";else->s}

@Composable
fun EnhancedWaitlistScreen(pin:String,extras:SlotellyExtras){
    var rows by remember{mutableStateOf<List<WaitlistItem>>(emptyList())}
    var tab by remember{mutableStateOf("active")}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<WaitlistItem?>(null)}
    var reload by remember{mutableIntStateOf(0)}

    LaunchedEffect(pin,reload){
        loading=true;error=""
        runCatching{extras.waitlistAll(pin)}.onSuccess{rows=it}.onFailure{error=it.message?:"Не удалось обновить Ловец"}
        loading=false
    }
    val visible=rows.filter{if(tab=="active")it.status in setOf("active","offered") else it.status !in setOf("active","offered")}

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Row(Modifier.fillMaxWidth()){
            Column(Modifier.weight(1f)){
                Text("Ловец окошек",style=MaterialTheme.typography.headlineSmall)
                Text("Активных: ${rows.count{it.status in setOf("active","offered")}} · История: ${rows.count{it.status !in setOf("active","offered")}}",style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={reload++}){Text("↻")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=tab=="active",onClick={tab="active"},label={Text("Активные")})
            FilterChip(selected=tab=="history",onClick={tab="history"},label={Text("История")})
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=8.dp)){
            items(visible,key={it.id}){r->
                Card(Modifier.fillMaxWidth().clickable{selected=r}){
                    Column(Modifier.padding(12.dp)){
                        Row(Modifier.fillMaxWidth()){
                            Text(r.clientName,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                            Text(ewlStatus(r.status))
                        }
                        if(r.phone.isNotBlank())Text(r.phone,style=MaterialTheme.typography.bodySmall)
                        Text(r.desiredText.ifBlank{"Без текста пожелания"})
                        val range=listOfNotNull(r.dateFrom,r.dateTo).joinToString(" — ")
                        val tm=listOfNotNull(r.timeFrom?.take(5),r.timeTo?.take(5)).joinToString("–")
                        if(range.isNotBlank()||tm.isNotBlank())Text(listOf(range,tm).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        r.appointmentStart?.let{Text("Текущая запись: ${ewlDt(it)}",style=MaterialTheme.typography.bodySmall)}
                    }
                }
            }
        }
    }

    selected?.let{item->
        WaitlistDetailOverlay(pin,extras,item,onClose={selected=null},onChanged={reload++;selected=null})
    }
}

@Composable
private fun WaitlistDetailOverlay(
    pin:String,extras:SlotellyExtras,item:WaitlistItem,onClose:()->Unit,onChanged:()->Unit
){
    val scope=rememberCoroutineScope()
    var detail by remember(item.id){mutableStateOf<WaitlistDetail?>(null)}
    var loading by remember{mutableStateOf(true)}
    var editing by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var desired by remember(item.id){mutableStateOf(item.desiredText)}
    var dateFrom by remember(item.id){mutableStateOf(item.dateFrom.orEmpty())}
    var dateTo by remember(item.id){mutableStateOf(item.dateTo.orEmpty())}
    var timeFrom by remember(item.id){mutableStateOf(item.timeFrom?.take(5).orEmpty())}
    var timeTo by remember(item.id){mutableStateOf(item.timeTo?.take(5).orEmpty())}
    var messenger by remember(item.id){mutableStateOf(item.preferredMessenger)}

    LaunchedEffect(item.id){
        runCatching{extras.waitlistDetail(pin,item.id)}.onSuccess{detail=it}.onFailure{error=it.message?:"Не удалось загрузить"}
        loading=false
    }
    FullOverlay(onClose){
        Row(Modifier.fillMaxWidth()){
            Column(Modifier.weight(1f)){
                Text(item.clientName,style=MaterialTheme.typography.headlineSmall)
                Text(ewlStatus(detail?.request?.status?:item.status),style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={editing=!editing}){Text(if(editing)"Отмена" else "Изменить")}
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)

        if(editing){
            OutlinedTextField(desired,{desired=it},label={Text("Пожелание")},modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedTextField(dateFrom,{dateFrom=it},label={Text("Дата от")},placeholder={Text("YYYY-MM-DD")},modifier=Modifier.weight(1f))
                OutlinedTextField(dateTo,{dateTo=it},label={Text("Дата до")},placeholder={Text("YYYY-MM-DD")},modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedTextField(timeFrom,{timeFrom=it},label={Text("Время от")},placeholder={Text("10:00")},modifier=Modifier.weight(1f))
                OutlinedTextField(timeTo,{timeTo=it},label={Text("Время до")},placeholder={Text("19:00")},modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(messenger,{messenger=it},label={Text("Мессенджер")},modifier=Modifier.fillMaxWidth())
            Button(onClick={scope.launch{
                runCatching{extras.updateWaitlist(pin,item,desired,dateFrom.ifBlank{null},dateTo.ifBlank{null},timeFrom.ifBlank{null},timeTo.ifBlank{null},messenger)}
                    .onSuccess{detail=it;editing=false;onChanged()}.onFailure{error=it.message?:"Не удалось сохранить"}
            }},modifier=Modifier.fillMaxWidth()){Text("Сохранить")}
        }else{
            val r=detail?.request?:item
            Text("Пожелание",fontWeight=FontWeight.SemiBold);Text(r.desiredText.ifBlank{"—"})
            Text("Даты: ${r.dateFrom?:"—"} — ${r.dateTo?:"—"}",style=MaterialTheme.typography.bodySmall)
            Text("Время: ${r.timeFrom?.take(5)?:"—"} — ${r.timeTo?.take(5)?:"—"}",style=MaterialTheme.typography.bodySmall)
            if(r.preferredMessenger.isNotBlank())Text("Канал: ${r.preferredMessenger}",style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Text("Предложенные окна",style=MaterialTheme.typography.titleMedium)
        val offers=detail?.offers.orEmpty()
        if(offers.isEmpty())Text("Предложений ещё не было",style=MaterialTheme.typography.bodySmall)
        else offers.take(8).forEach{o->
            Card(Modifier.fillMaxWidth().padding(vertical=2.dp)){
                Row(Modifier.padding(9.dp).fillMaxWidth()){
                    Text(ewlDt(o.offeredStart),modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                    Text(ewlStatus(o.status),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("История",style=MaterialTheme.typography.titleMedium)
        val history=detail?.history.orEmpty()
        if(history.isEmpty())Text("История пока пустая",style=MaterialTheme.typography.bodySmall)
        else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            items(history,key={it.id}){h->
                Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                    Row(Modifier.fillMaxWidth()){
                        Text(ewlEvent(h.eventType),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        Text(ewlDt(h.createdAt),style=MaterialTheme.typography.labelSmall)
                    }
                    if(h.eventText.isNotBlank())Text(h.eventText,style=MaterialTheme.typography.bodySmall)
                    h.offeredAt?.let{Text("Окно: ${ewlDt(it)}",style=MaterialTheme.typography.bodySmall)}
                    HorizontalDivider(Modifier.padding(top=5.dp))
                }
            }
        }
        val current=detail?.request?.status?:item.status
        if(current in setOf("active","offered")){
            OutlinedButton(onClick={scope.launch{
                runCatching{extras.updateWaitlist(pin,item,desired,dateFrom.ifBlank{null},dateTo.ifBlank{null},timeFrom.ifBlank{null},timeTo.ifBlank{null},messenger,"cancelled")}.onSuccess{onChanged()}
            }},modifier=Modifier.fillMaxWidth()){Text("Закрыть заявку")}
        }else{
            OutlinedButton(onClick={scope.launch{
                runCatching{extras.updateWaitlist(pin,item,desired,dateFrom.ifBlank{null},dateTo.ifBlank{null},timeFrom.ifBlank{null},timeTo.ifBlank{null},messenger,"active")}.onSuccess{onChanged()}
            }},modifier=Modifier.fillMaxWidth()){Text("Вернуть в активные")}
        }
    }
}
