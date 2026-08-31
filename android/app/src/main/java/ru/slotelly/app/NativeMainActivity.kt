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
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8F4F68))) {
                NativeSlotellyRoot(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeSlotellyRoot(repo: SlotellyRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extras = remember { SlotellyExtras() }
    var pin by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(NativeTab.CALENDAR) }
    var selectedAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var editAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showNewClient by remember { mutableStateOf(false) }
    var showAvailability by remember { mutableStateOf(false) }
    var paymentAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }

    val appts by repo.appointments().collectAsStateWithLifecycle(emptyList())
    val clients by repo.clients().collectAsStateWithLifecycle(emptyList())
    val services by repo.services().collectAsStateWithLifecycle(emptyList())
    val overrides by repo.overrides().collectAsStateWithLifecycle(emptyList())
    val state by repo.appState().collectAsStateWithLifecycle(null)

    fun syncNow() { scope.launch { runCatching { repo.sync(pin) } } }

    LaunchedEffect(Unit) {
        val saved = context.dataStore.data.first()[PIN_KEY].orEmpty()
        pin = saved
        unlocked = saved.isNotBlank()
        initialized = true
        if (saved.isNotBlank()) launch { runCatching { repo.sync(saved) } }
    }

    if (!initialized) {
        Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        return
    }

    if (!unlocked) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Slotelly NEW", style = MaterialTheme.typography.headlineLarge)
                Text("Android 0.5 beta", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(pin, { pin = it }, label = { Text("PIN мастера") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        if (pin.isBlank()) return@launch
                        context.dataStore.edit { it[PIN_KEY] = pin }
                        unlocked = true
                        launch { runCatching { repo.sync(pin) } }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Открыть Slotelly NEW") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Slotelly NEW")
                        Text("Android 0.5 beta", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    if (tab == NativeTab.CALENDAR) TextButton(onClick = { showAvailability = true }) { Text("Окошки") }
                    val fresh = state?.syncedAt?.let { System.currentTimeMillis() - it < 120_000 } == true
                    Text(if (fresh) "✓" else "○", modifier = Modifier.padding(horizontal = 6.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == NativeTab.CALENDAR, onClick = { tab = NativeTab.CALENDAR }, icon = { Text("▦") }, label = { Text("Календарь") })
                NavigationBarItem(selected = tab == NativeTab.CLIENTS, onClick = { tab = NativeTab.CLIENTS }, icon = { Text("◉") }, label = { Text("Клиенты") })
                NavigationBarItem(selected = tab == NativeTab.WAITLIST, onClick = { tab = NativeTab.WAITLIST }, icon = { Text("⌁") }, label = { Text("Ловец") })
                NavigationBarItem(selected = tab == NativeTab.FINANCE, onClick = { tab = NativeTab.FINANCE }, icon = { Text("₽") }, label = { Text("Финансы") })
                NavigationBarItem(selected = tab == NativeTab.MORE, onClick = { tab = NativeTab.MORE }, icon = { Text("⋯") }, label = { Text("Ещё") })
            }
        },
        floatingActionButton = {
            if (tab == NativeTab.CALENDAR) FloatingActionButton(onClick = { editAppointment = null; showEditor = true }) { Text("+") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                NativeTab.CALENDAR -> EnhancedCalendarScreen(appts, onOpen = { selectedAppointment = it })
                NativeTab.CLIENTS -> EnhancedClientsScreen(
                    pin = pin,
                    extras = extras,
                    clients = clients,
                    appointments = appts,
                    onNew = { showNewClient = true },
                    onOpenAppointment = { selectedAppointment = it },
                    onRefresh = { syncNow() }
                )
                NativeTab.WAITLIST -> EnhancedWaitlistScreen(pin, extras)
                NativeTab.FINANCE -> EnhancedFinanceScreen(pin, extras, appts)
                NativeTab.MORE -> EnhancedMoreScreen(
                    pin = pin,
                    settingsJson = state?.settingsJson ?: "{}",
                    syncedAt = state?.syncedAt,
                    onSync = { syncNow() },
                    onSettingsChanged = { syncNow() }
                )
            }
        }
    }

    selectedAppointment?.let { a ->
        AppointmentDialog(
            appointment = a,
            onClose = { selectedAppointment = null },
            onEdit = { editAppointment = a; selectedAppointment = null; showEditor = true },
            onCancel = {
                scope.launch { repo.cancel(a.id); launch { runCatching { repo.flush(pin) } } }
                selectedAppointment = null
            },
            onUnpaid = {
                scope.launch { repo.markUnpaid(a.id); launch { runCatching { repo.flush(pin) } } }
                selectedAppointment = null
            },
            onPayment = { selectedAppointment = null; paymentAppointment = a }
        )
    }

    if (showEditor) {
        MultiServiceAppointmentEditor(
            existing = editAppointment,
            clients = clients,
            services = services,
            onClose = { showEditor = false; editAppointment = null },
            onSave = { client, selectedServices, startsAt, comment ->
                scope.launch {
                    repo.saveAppointment(editAppointment?.id, client, selectedServices, startsAt, comment)
                    launch { runCatching { repo.flush(pin) } }
                }
                showEditor = false
                editAppointment = null
            }
        )
    }

    if (showAvailability) {
        AvailabilityOverlay(
            overrides = overrides,
            repo = repo,
            pin = pin,
            onClose = { showAvailability = false }
        )
    }

    paymentAppointment?.let { a ->
        PaymentDialog(a, onClose = { paymentAppointment = null }) { cash, card, other ->
            scope.launch { repo.payment(a.id, cash, card, other); launch { runCatching { repo.flush(pin) } } }
            paymentAppointment = null
        }
    }

    if (showNewClient) {
        NewClientDialog(onClose = { showNewClient = false }) { name, phone, messenger ->
            scope.launch { runCatching { repo.createClient(pin, name, phone, messenger) } }
            showNewClient = false
        }
    }
}
