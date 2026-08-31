package ru.slotelly.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.slotelly.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ECL_ZONE = ZoneId.of("Europe/Moscow")
private val ECL_RU = Locale("ru", "RU")
private fun eclDigits(s: String) = s.filter(Char::isDigit)
private fun eclServiceNames(json: String): String = runCatching {
    com.google.gson.JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
        val o=e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")
private fun eclDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ECL_ZONE).format(DateTimeFormatter.ofPattern("dd.MM (EEE) HH:mm", ECL_RU)).replace(".", "")
}.getOrDefault(iso)
private fun eclTotal(json: String): Int = runCatching {
    com.google.gson.JsonParser.parseString(json).asJsonArray.sumOf { e ->
        val o=e.asJsonObject
        o["price"]?.asDouble ?: o["actual_price"]?.asDouble ?: 0.0
    }.toInt()
}.getOrDefault(0)

@Composable
fun EnhancedClientsScreen(
    pin: String,
    extras: SlotellyExtras,
    clients: List<ClientEntity>,
    appointments: List<AppointmentEntity>,
    onNew: () -> Unit,
    onOpenAppointment: (AppointmentEntity) -> Unit,
    onRefresh: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ClientEntity?>(null) }
    val qDigits=eclDigits(query)
    val rows=remember(clients,query) {
        val sorted=clients.sortedBy { it.name.lowercase(ECL_RU) }
        val q=query.trim()
        if(q.isBlank()) sorted else sorted.filter { c ->
            c.name.contains(q,true) || (qDigits.isNotBlank() && eclDigits(c.phone).contains(qDigits))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)) {
        Row {
            OutlinedTextField(query,{query=it},label={Text("Имя или телефон")},singleLine=true,modifier=Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick=onNew){Text("+")}
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            items(rows,key={it.id}) { c ->
                Card(Modifier.fillMaxWidth().clickable{selected=c}) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name,fontWeight=FontWeight.SemiBold)
                        if(c.phone.isNotBlank()) Text(c.phone)
                        val last=eclServiceNames(c.lastServicesJson)
                        if(last.isNotBlank()) Text("Последнее: $last",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    selected?.let { c ->
        EnhancedClientCard(
            pin=pin,
            extras=extras,
            client=c,
            appointments=appointments.filter{it.clientId==c.id}.sortedByDescending{it.startsAt},
            onClose={selected=null},
            onSaved={onRefresh()},
            onOpenAppointment={selected=null;onOpenAppointment(it)}
        )
    }
}

@Composable
private fun EnhancedClientCard(
    pin:String,
    extras:SlotellyExtras,
    client:ClientEntity,
    appointments:List<AppointmentEntity>,
    onClose:()->Unit,
    onSaved:()->Unit,
    onOpenAppointment:(AppointmentEntity)->Unit
){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var extra by remember(client.id){mutableStateOf<ClientExtra?>(null)}
    var name by remember(client.id){mutableStateOf(client.name)}
    var phone by remember(client.id){mutableStateOf(client.phone)}
    var messenger by remember(client.id){mutableStateOf(client.messenger)}
    var birthDate by remember(client.id){mutableStateOf("")}
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(client.id){
        runCatching{extras.clientExtra(pin,client.id)}.onSuccess{
            extra=it; birthDate=it.birthDate.orEmpty()
        }
    }

    FullOverlay(onClose) {
        Row(Modifier.fillMaxWidth()){
            Text(if(editing)"Редактирование клиента" else client.name,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
            TextButton(onClick={editing=!editing}){Text(if(editing)"Отмена" else "Редактировать")}
        }
        if(editing){
            OutlinedTextField(name,{name=it},label={Text("Имя")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(phone,{phone=it},label={Text("Телефон")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(birthDate,{birthDate=it},label={Text("Дата рождения YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(messenger,{messenger=it},label={Text("Предпочтительный мессенджер")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Button(onClick={
                loading=true;error=""
                scope.launch{
                    runCatching{extras.updateClient(pin,client.id,name,phone,messenger,birthDate.ifBlank{null})}
                        .onSuccess{extra=it;editing=false;onSaved()}
                        .onFailure{error=it.message?:"Не удалось сохранить"}
                    loading=false
                }
            },enabled=!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Сохраняю…" else "Сохранить")}
        } else {
            if(client.phone.isNotBlank()) Text(client.phone,style=MaterialTheme.typography.titleMedium)
            extra?.birthDate?.takeIf{it.isNotBlank()}?.let{Text("Дата рождения: $it")}
            if(client.messenger.isNotBlank()) Text("Предпочтительно: ${client.messenger}")
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp)){
                Text("Telegram",fontWeight=FontWeight.SemiBold)
                when{
                    extra?.telegramLinked==true -> Text("Подключён ✓ ${extra?.telegramUsername?.takeIf{it.isNotBlank()}?.let{"@$it"}?:""}")
                    else -> {
                        Text("Не подключён. Для автоматической отправки клиент должен один раз открыть персональную ссылку в боте.",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={
                            scope.launch{
                                runCatching{extras.createTelegramLink(pin,client.id)}.onSuccess{url->
                                    if(url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure{error=it.message?:"Не удалось создать ссылку"}
                            }
                        }){Text("Подключить Telegram")}
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("История записей",style=MaterialTheme.typography.titleMedium)
        if(appointments.isEmpty()) Text("Записей нет",style=MaterialTheme.typography.bodySmall)
        else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=6.dp)){
            items(appointments,key={it.id}){a->
                Card(Modifier.fillMaxWidth().clickable{onOpenAppointment(a)}){
                    Column(Modifier.padding(10.dp)){
                        Row(Modifier.fillMaxWidth()){
                            Text(eclDate(a.startsAt),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                            val total=eclTotal(a.servicesJson);if(total>0)Text("$total ₽")
                        }
                        Text(eclServiceNames(a.servicesJson))
                        Text(when(a.status){"completed_paid"->"Оплачено";"completed_unpaid"->"Без оплаты";"cancelled"->"Отменено";else->"Запись"},style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
