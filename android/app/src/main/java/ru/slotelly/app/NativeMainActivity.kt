package ru.slotelly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.slotelly.app.data.*
import ru.slotelly.app.sync.PIN_KEY
import ru.slotelly.app.sync.dataStore
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NativeTab { CALENDAR, CLIENTS, WAITLIST, FINANCE }

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
                Text("Slotelly", style = MaterialTheme.typography.headlineLarge)
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
                }, modifier = Modifier.fillMaxWidth()) { Text("Открыть Slotelly") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slotelly") },
                actions = {
                    if (tab == NativeTab.CALENDAR) TextButton(onClick = { showAvailability = true }) { Text("Окошки") }
                    val fresh = state?.syncedAt?.let { System.currentTimeMillis() - it < 120_000 } == true
                    Text(if (fresh) "✓" else "○", modifier = Modifier.padding(horizontal = 6.dp))
                    TextButton(onClick = { scope.launch { runCatching { repo.sync(pin) } } }) { Text("Обновить") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == NativeTab.CALENDAR, onClick = { tab = NativeTab.CALENDAR }, icon = { Text("▦") }, label = { Text("Календарь") })
                NavigationBarItem(selected = tab == NativeTab.CLIENTS, onClick = { tab = NativeTab.CLIENTS }, icon = { Text("◉") }, label = { Text("Клиенты") })
                NavigationBarItem(selected = tab == NativeTab.WAITLIST, onClick = { tab = NativeTab.WAITLIST }, icon = { Text("⌁") }, label = { Text("Ловец") })
                NavigationBarItem(selected = tab == NativeTab.FINANCE, onClick = { tab = NativeTab.FINANCE }, icon = { Text("₽") }, label = { Text("Финансы") })
            }
        },
        floatingActionButton = {
            if (tab == NativeTab.CALENDAR) FloatingActionButton(onClick = { editAppointment = null; showEditor = true }) { Text("+") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                NativeTab.CALENDAR -> CalendarScreen(appts, onOpen = { selectedAppointment = it })
                NativeTab.CLIENTS -> ClientsScreen(clients, onNew = { showNewClient = true })
                NativeTab.WAITLIST -> WaitlistNativeScreen(pin, extras)
                NativeTab.FINANCE -> ServerFinanceScreen(pin, extras, appts)
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
        FastAppointmentEditor(
            existing = editAppointment,
            clients = clients,
            services = services,
            onClose = { showEditor = false; editAppointment = null },
            onSave = { client, service, startsAt, price, duration, comment ->
                scope.launch {
                    repo.saveAppointment(editAppointment?.id, client, service, startsAt, price, duration, comment)
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

@Composable
private fun WaitlistNativeScreen(pin: String, extras: SlotellyExtras) {
    var rows by remember { mutableStateOf<List<WaitlistItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(pin, reload) {
        loading = true
        error = ""
        runCatching { extras.waitlist(pin) }
            .onSuccess { rows = it }
            .onFailure { error = "Не удалось обновить Ловец. Показываю последнее загруженное состояние." }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ловец окошек", style = MaterialTheme.typography.headlineSmall)
                Text("Активных заявок: ${rows.size}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { reload++ }) { Text("Обновить") }
        }
        if (loading && rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (!loading && rows.isEmpty() && error.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Активных заявок нет") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(rows, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(r.clientName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(if (r.status == "offered") "Предложено" else "Ищем")
                            }
                            if (r.phone.isNotBlank()) Text(r.phone)
                            if (r.desiredText.isNotBlank()) Text(r.desiredText, style = MaterialTheme.typography.bodyMedium)
                            r.appointmentStart?.let { Text("Текущая запись: ${formatMsk(it)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerFinanceScreen(pin: String, extras: SlotellyExtras, localAppointments: List<AppointmentEntity>) {
    var period by remember { mutableStateOf("Месяц") }
    var summary by remember { mutableStateOf<ReportSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val zone = ZoneId.of("Europe/Moscow")
    val now = ZonedDateTime.now(zone)

    fun range(): Pair<Instant, Instant> = when (period) {
        "Сегодня" -> now.toLocalDate().atStartOfDay(zone).toInstant() to now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        "Неделя" -> {
            val monday = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong())
            monday.atStartOfDay(zone).toInstant() to monday.plusDays(7).atStartOfDay(zone).toInstant()
        }
        else -> now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant() to now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant()
    }

    LaunchedEffect(pin, period) {
        loading = true
        error = ""
        val (from, to) = range()
        runCatching { extras.report(pin, from, to) }
            .onSuccess { summary = it }
            .onFailure { error = "Серверный отчёт сейчас недоступен. Ниже локальные данные телефона." }
        loading = false
    }

    val fallbackTotal = localAppointments.filter { it.status == "completed_paid" }.sumOf { localPaymentTotal(it.paymentsJson) }
    val s = summary
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Финансы", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Сегодня", "Неделя", "Месяц").forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))
        Text("${(s?.total ?: fallbackTotal).toInt()} ₽", style = MaterialTheme.typography.headlineLarge)
        Text("Выручка · $period")
        Spacer(Modifier.height(18.dp))
        if (s != null) {
            Text("Наличные: ${s.cash.toInt()} ₽")
            Text("Карта: ${s.card.toInt()} ₽")
            if (s.other > 0) Text("Другое: ${s.other.toInt()} ₽")
            Spacer(Modifier.height(8.dp))
            Text("Оплачено записей: ${s.paidCount}")
            Text("Завершено без оплаты: ${s.unpaidCount}")
        } else {
            Text("Локально оплачено: ${fallbackTotal.toInt()} ₽")
        }
    }
}

private fun formatMsk(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.of("Europe/Moscow"))
        .format(DateTimeFormatter.ofPattern("dd.MM (EEE) в HH:mm", Locale("ru", "RU")))
}.getOrDefault(iso)

private fun localPaymentTotal(json: String): Double = runCatching {
    com.google.gson.JsonParser.parseString(json).asJsonArray.sumOf { e ->
        val o = e.asJsonObject
        o["total"]?.asDouble ?: o["total_amount"]?.asDouble ?: 0.0
    }
}.getOrDefault(0.0)
