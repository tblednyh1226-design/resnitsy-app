package ru.slotelly.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.slotelly.app.data.*
import java.time.*

@Composable
fun EnhancedFinanceScreen(pin:String,extras:SlotellyExtras,localAppointments:List<AppointmentEntity>){
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Text("Финансы",style=MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        FinanceReport(pin,extras,localAppointments)
    }
}

@Composable
private fun FinanceReport(pin:String,extras:SlotellyExtras,localAppointments:List<AppointmentEntity>){
    var period by remember{mutableStateOf("Месяц")}
    var fromText by remember{mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())}
    var toText by remember{mutableStateOf(LocalDate.now().toString())}
    var summary by remember{mutableStateOf<ReportSummary?>(null)}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val zone=ZoneId.of("Europe/Moscow")

    fun range():Pair<Instant,Instant>{
        val now=ZonedDateTime.now(zone)
        return when(period){
            "День"->now.toLocalDate().atStartOfDay(zone).toInstant() to now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            "Неделя"->{val monday=now.toLocalDate().minusDays((now.dayOfWeek.value-1).toLong());monday.atStartOfDay(zone).toInstant() to monday.plusDays(7).atStartOfDay(zone).toInstant()}
            "Период"->{
                val f=runCatching{LocalDate.parse(fromText)}.getOrElse{now.toLocalDate()}
                val t=runCatching{LocalDate.parse(toText)}.getOrElse{f}
                f.atStartOfDay(zone).toInstant() to t.plusDays(1).atStartOfDay(zone).toInstant()
            }
            else->{val first=now.toLocalDate().withDayOfMonth(1);first.atStartOfDay(zone).toInstant() to first.plusMonths(1).atStartOfDay(zone).toInstant()}
        }
    }

    LaunchedEffect(pin,period,fromText,toText){
        if(period=="Период" && (runCatching{LocalDate.parse(fromText)}.isFailure||runCatching{LocalDate.parse(toText)}.isFailure))return@LaunchedEffect
        loading=true;error=""
        val (from,to)=range()
        runCatching{extras.report(pin,from,to)}.onSuccess{summary=it}.onFailure{summary=null;error="Серверный отчёт недоступен. Показываю локальные данные за выбранный период."}
        loading=false
    }

    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
        listOf("День","Неделя","Месяц","Период").forEach{p->FilterChip(selected=period==p,onClick={period=p},label={Text(p)})}
    }
    if(period=="Период"){
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedTextField(fromText,{fromText=it},label={Text("С")},placeholder={Text("YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
            OutlinedTextField(toText,{toText=it},label={Text("По")},placeholder={Text("YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
        }
    }
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))

    val (from,to)=range()
    val localRows=localAppointments.filter{a->
        val t=runCatching{Instant.parse(a.startsAt)}.getOrNull()
        t!=null && !t.isBefore(from) && t.isBefore(to)
    }
    val s=summary
    val localFact=localRows.filter{it.status=="completed_paid"}.sumOf{localPaymentTotalEnhanced(it.paymentsJson)}
    val localPlan=localRows.filter{it.status!="cancelled"}.sumOf{localServiceTotalEnhanced(it.servicesJson)}
    val localPaidCount=localRows.count{it.status=="completed_paid"}
    val localUnpaidCount=localRows.count{it.status=="completed_unpaid"}
    val localCash=localRows.filter{it.status=="completed_paid"}.sumOf{localPaymentPartEnhanced(it.paymentsJson,"cash")}
    val localCard=localRows.filter{it.status=="completed_paid"}.sumOf{localPaymentPartEnhanced(it.paymentsJson,"card")}
    val localOther=localRows.filter{it.status=="completed_paid"}.sumOf{localPaymentPartEnhanced(it.paymentsJson,"other")}

    val fact=s?.total?:localFact
    val plan=s?.plan?:localPlan
    val cash=s?.cash?:localCash
    val nonCash=(s?.card?:localCard)+(s?.other?:localOther)
    val paidCount=s?.paidCount?:localPaidCount
    val unpaidCount=s?.unpaidCount?:localUnpaidCount

    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        MetricCard("План",plan,"Стоимость записей",Modifier.weight(1f))
        MetricCard("Факт",fact,"Проведено оплат",Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        MetricCard("Наличные",cash,"Факт",Modifier.weight(1f))
        MetricCard("Безнал",nonCash,"Карта + другое",Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp)){
            Text("Итоги",fontWeight=FontWeight.SemiBold)
            Text("Выполнение плана: ${if(plan>0)((fact/plan)*100).toInt() else 0}%")
            Text("Оплачено записей: $paidCount")
            Text("Завершено без оплаты: $unpaidCount")
        }
    }
}

@Composable
private fun MetricCard(title:String,value:Double,sub:String,modifier:Modifier){
    Card(modifier){Column(Modifier.padding(12.dp)){
        Text(title,style=MaterialTheme.typography.bodySmall)
        Text("${value.toInt()} ₽",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(sub,style=MaterialTheme.typography.labelSmall)
    }}
}

private fun localPaymentTotalEnhanced(json:String):Double=runCatching{
    com.google.gson.JsonParser.parseString(json).asJsonArray.sumOf{e->val o=e.asJsonObject;o["total"]?.asDouble?:o["total_amount"]?.asDouble?:0.0}
}.getOrDefault(0.0)
private fun localPaymentPartEnhanced(json:String,key:String):Double=runCatching{
    com.google.gson.JsonParser.parseString(json).asJsonArray.sumOf{e->
        val o=e.asJsonObject
        when(key){
            "cash"->o["cash"]?.asDouble?:o["cash_amount"]?.asDouble?:0.0
            "card"->o["card"]?.asDouble?:o["card_amount"]?.asDouble?:0.0
            else->o["other"]?.asDouble?:o["other_amount"]?.asDouble?:0.0
        }
    }
}.getOrDefault(0.0)
private fun localServiceTotalEnhanced(json:String):Double=runCatching{
    com.google.gson.JsonParser.parseString(json).asJsonArray.sumOf{e->val o=e.asJsonObject;o["price"]?.asDouble?:o["actual_price"]?.asDouble?:0.0}
}.getOrDefault(0.0)
