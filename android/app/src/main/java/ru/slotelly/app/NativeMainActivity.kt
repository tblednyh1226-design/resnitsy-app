package ru.slotelly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.slotelly.app.data.*
import ru.slotelly.app.sync.PIN_KEY
import ru.slotelly.app.sync.dataStore

private enum class NativeTab { CALENDAR, CLIENTS, WAITLIST, FINANCE, MORE }

class NativeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SlotellyRepository((application as SlotellyApp).db)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8F4F68))) { NativeSlotellyRoot(repo) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeSlotellyRoot(repo: SlotellyRepository) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val extras=remember{SlotellyExtras()}
    var pin by remember{mutableStateOf("")};var initialized by remember{mutableStateOf(false)};var unlocked by remember{mutableStateOf(false)};var loginBusy by remember{mutableStateOf(false)};var loginError by remember{mutableStateOf("")}
    var tab by remember{mutableStateOf(NativeTab.CALENDAR)};var selectedAppointment by remember{mutableStateOf<AppointmentEntity?>(null)};var editAppointment by remember{mutableStateOf<AppointmentEntity?>(null)};var showEditor by remember{mutableStateOf(false)};var showNewClient by remember{mutableStateOf(false)};var showAvailability by remember{mutableStateOf(false)};var paymentAppointment by remember{mutableStateOf<AppointmentEntity?>(null)}
    val appts by repo.appointments().collectAsStateWithLifecycle(emptyList());val clients by repo.clients().collectAsStateWithLifecycle(emptyList());val services by repo.services().collectAsStateWithLifecycle(emptyList());val overrides by repo.overrides().collectAsStateWithLifecycle(emptyList());val state by repo.appState().collectAsStateWithLifecycle(null)
    fun syncNow(){scope.launch{runCatching{repo.sync(pin)}}}
    LaunchedEffect(Unit){val saved=context.dataStore.data.first()[PIN_KEY].orEmpty();pin=saved;unlocked=saved.isNotBlank();initialized=true;if(saved.isNotBlank())launch{runCatching{repo.sync(saved)}}}
    if(!initialized){Surface(Modifier.fillMaxSize()){Box(Modifier.fillMaxSize())};return}
    if(!unlocked){Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Text("Slotelly NEW",style=MaterialTheme.typography.headlineLarge);Text("Новая Android-версия 0.5.1",style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(18.dp));OutlinedTextField(pin,{pin=it;loginError=""},label={Text("PIN мастера")},singleLine=true,enabled=!loginBusy,modifier=Modifier.fillMaxWidth());if(loginError.isNotBlank()){Spacer(Modifier.height(6.dp));Text(loginError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)};Spacer(Modifier.height(12.dp));Button(onClick={if(pin.isBlank()||loginBusy)return@Button;loginBusy=true;loginError="";scope.launch{runCatching{repo.sync(pin)}.onSuccess{context.dataStore.edit{it[PIN_KEY]=pin};unlocked=true}.onFailure{loginError=if(it.message?.contains("PIN",ignoreCase=true)==true)"Неверный PIN" else "Не удалось проверить PIN. Проверьте интернет и попробуйте ещё раз."};loginBusy=false}},enabled=!loginBusy&&pin.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(loginBusy)"Проверяю…" else "Открыть Slotelly NEW")}}};return}
    Scaffold(
        topBar={TopAppBar(title={Text("Slotelly")},actions={if(tab==NativeTab.CALENDAR)TextButton(onClick={showAvailability=true}){Text("Окошки")};val fresh=state?.syncedAt?.let{System.currentTimeMillis()-it<120_000}==true;Text(if(fresh)"✓" else "○",modifier=Modifier.padding(horizontal=6.dp))})},
        bottomBar={NavigationBar{NavigationBarItem(selected=tab==NativeTab.CALENDAR,onClick={tab=NativeTab.CALENDAR},icon={Text("▦")},label={Text("Календарь",maxLines=1)});NavigationBarItem(selected=tab==NativeTab.CLIENTS,onClick={tab=NativeTab.CLIENTS},icon={Text("◉")},label={Text("Клиенты",maxLines=1)});NavigationBarItem(selected=tab==NativeTab.WAITLIST,onClick={tab=NativeTab.WAITLIST},icon={Text("⌁")},label={Text("Ловец",maxLines=1)});NavigationBarItem(selected=tab==NativeTab.FINANCE,onClick={tab=NativeTab.FINANCE},icon={Text("₽")},label={Text("Финансы",maxLines=1)});NavigationBarItem(selected=tab==NativeTab.MORE,onClick={tab=NativeTab.MORE},icon={Text("⋯")},label={Text("Ещё",maxLines=1)})}},
        floatingActionButton={if(tab==NativeTab.CALENDAR)FloatingActionButton(onClick={editAppointment=null;showEditor=true}){Text("+")}}
    ){pad->Box(Modifier.padding(pad).fillMaxSize()){when(tab){NativeTab.CALENDAR->EnhancedCalendarScreen(appts){selectedAppointment=it};NativeTab.CLIENTS->EnhancedClientsScreen(pin,extras,clients,appts,{showNewClient=true},{selectedAppointment=it},{syncNow()});NativeTab.WAITLIST->EnhancedWaitlistScreen(pin,extras);NativeTab.FINANCE->EnhancedFinanceScreen(pin,extras,appts);NativeTab.MORE->EnhancedMoreScreen(pin,state?.settingsJson?:"{}",state?.syncedAt,{syncNow()},{syncNow()})}}}
    selectedAppointment?.let{a->AppointmentDialog(a,{selectedAppointment=null},{editAppointment=a;selectedAppointment=null;showEditor=true},{scope.launch{repo.cancel(a.id);launch{runCatching{repo.flush(pin)}}};selectedAppointment=null},{scope.launch{repo.markUnpaid(a.id);launch{runCatching{repo.flush(pin)}}};selectedAppointment=null},{selectedAppointment=null;paymentAppointment=a})}
    if(showEditor)MultiServiceAppointmentEditor(editAppointment,clients,services,{showEditor=false;editAppointment=null}){client,selectedServices,startsAt,comment->scope.launch{repo.saveAppointment(editAppointment?.id,client,selectedServices,startsAt,comment);launch{runCatching{repo.flush(pin)}}};showEditor=false;editAppointment=null}
    if(showAvailability)AvailabilityOverlay(overrides,repo,pin){showAvailability=false}
    paymentAppointment?.let{a->PaymentDialog(a,{paymentAppointment=null}){cash,card,other->scope.launch{repo.payment(a.id,cash,card,other);launch{runCatching{repo.flush(pin)}}};paymentAppointment=null}}
    if(showNewClient)NewClientDialog({showNewClient=false}){name,phone,messenger->scope.launch{runCatching{repo.createClient(pin,name,phone,messenger)}};showNewClient=false}
}
