package ru.slotelly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private enum class AppTab { CALENDAR, CLIENTS, WAITLIST, FINANCE }

class SlotellyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SlotellyRepository((application as SlotellyApp).db)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8F4F68))) {
                SlotellyNativeRoot(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotellyNativeRoot(repo: SlotellyRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val extras = remember { SlotellyExtras() }
    var pin by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(AppTab.CALENDAR) }
    var selected by remember { mutableStateOf<AppointmentEntity?>(null) }
    var editing by remember { mutableStateOf<AppointmentEntity?>(null) }
    var editor by remember { mutableStateOf(false) }
    var newClient by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<AppointmentEntity?>(null) }

    val appts by repo.appointments().collectAsStateWithLifecycle(emptyList())
    val clients by repo.clients().collectAsStateWithLifecycle(emptyList())
    val services by repo.services().collectAsStateWithLifecycle(emptyList())
    val overrides by repo.overrides().collectAsStateWithLifecycle(emptyList())
    val appState by repo.appState().collectAsStateWithLifecycle(null)

    LaunchedEffect(Unit) {
        pin = context.dataStore.data.first()[PIN_KEY].orEmpty()
        ready = true
        if (pin.isNotBlank()) launch { runCatching { repo.sync(pin) } }
    }

    if (!ready) return
    if (pin.isBlank()) {
        var entry by remember { mutableStateOf("") }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Slotelly", style = MaterialTheme.typography.headlineLarge)
                Text("Календарь работает локально. PIN нужен только для первой синхронизации.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(entry, { entry = it.filter(Char::isDigit) }, label = { Text("PIN мастера") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (entry.isBlank()) return@Button
                    scope.launch {
                        context.dataStore.edit { it[PIN_KEY] = entry }
                        pin = entry
                        launch { runCatching { repo.sync(entry) } }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Открыть") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slotelly") },
                actions = {
                    if (tab == AppTab.CALENDAR) TextButton(onClick = { availability = true }) { Text("Окошки") }
                    val fresh = appState?.syncedAt?.let { System.currentTimeMillis() - it < 120_000 } == true
                    Text(if (fresh) "✓" else "○", modifier = Modifier.padding(horizontal = 6.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == AppTab.CALENDAR, { tab = AppTab.CALENDAR }, { Text("▦") }, label = { Text("Календарь") })
                NavigationBarItem(tab == AppTab.CLIENTS, { tab = AppTab.CLIENTS }, { Text("◉") }, label = { Text("Клиенты") })
                NavigationBarItem(tab == AppTab.WAITLIST, { tab = AppTab.WAITLIST }, { Text("⌁") }, label = { Text("Ловец") })
                NavigationBarItem(tab == AppTab.FINANCE, { tab = AppTab.FINANCE }, { Text("₽") }, label = { Text("Финансы") })
            }
        },
        floatingActionButton = {
            if (tab == AppTab.CALENDAR) FloatingActionButton(onClick = { editing = null; editor = true }) { Text("+") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                AppTab.CALENDAR -> CalendarScreen(appts, onOpen = { selected = it })
                AppTab.CLIENTS -> FastClientsScreen(clients, appts, onNew = { newClient = true }, onAppointment = { selected = it })
                AppTab.WAITLIST -> NativeWaitlistScreen(pin, extras)
                AppTab.FINANCE -> NativeFinanceScreen(pin, extras, appts)
            }
        }
    }

    selected?.let { a ->
        AppointmentDialog(
            appointment = a,
            onClose = { selected = null },
            onEdit = { editing = a; selected = null; editor = true },
            onCancel = {
                selected = null
                scope.launch { repo.cancel(a.id); launch { runCatching { repo.flush(pin) } } }
            },
            onUnpaid = {
                selected = null
                scope.launch { repo.markUnpaid(a.id); launch { runCatching { repo.flush(pin) } } }
            },
            onPayment = { selected = null; paying = a }
        )
    }

    if (editor) {
        FastAppointmentEditor(
            existing = editing,
            clients = clients,
            services = services,
            onClose = { editor = false; editing = null },
            onSave = { client, service, startsAt, price, duration, comment ->
                val oldId = editing?.id
                editor = false
                editing = null
                scope.launch {
                    repo.saveAppointment(oldId, client, service, startsAt, price, duration, comment)
                    launch { runCatching { repo.flush(pin) } }
                }
            }
        )
    }

    if (availability) {
        AvailabilityOverlay(overrides = overrides, repo = repo, pin = pin, onClose = { availability = false })
    }

    paying?.let { a ->
        PaymentDialog(a, onClose = { paying = null }) { cash, card, other ->
            paying = null
            scope.launch { repo.payment(a.id, cash, card, other); launch { runCatching { repo.flush(pin) } } }
        }
    }

    if (newClient) {
        NewClientDialog(onClose = { newClient = false }) { name, phone, messenger ->
            newClient = false
            scope.launch {
                repo.createClientLocal(name, phone, messenger)
                launch { runCatching { repo.flush(pin) } }
            }
        }
    }
}

@Composable
private fun FastClientsScreen(
    clients: List<ClientEntity>,
    appointments: List<AppointmentEntity>,
    onNew: () -> Unit,
    onAppointment: (AppointmentEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ClientEntity?>(null) }
    val q = query.trim().lowercase()
    val qd = query.filter(Char::isDigit).takeLast(10)
    val filtered = remember(q, qd, clients) {
        clients.filter { c -> q.isBlank() || c.name.lowercase().contains(q) || (qd.isNotBlank() && c.phone.filter(Char::isDigit).contains(qd)) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, label = { Text("Поиск клиента") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = onNew) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(filtered, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { selected = c }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        if (c.id.startsWith("local-client-")) Text("Ждёт синхронизации", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    selected?.let { c ->
        val history = appointments.filter { it.clientId == c.id }.sortedByDescending { it.startsAt }
        FullOverlay({ selected = null }) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall)
            Text(c.phone)
            if (c.messenger.isNotBlank()) Text(c.messenger)
            Spacer(Modifier.height(12.dp))
            Text("Записи", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (history.isEmpty()) Text("Записей пока нет")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(history, key = { it.id }) { a ->
                    Card(Modifier.fillMaxWidth().clickable { selected = null; onAppointment(a) }) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${a.startsAt.take(10)} · ${runCatching { Instant.parse(a.startsAt).atZone(ZoneId.of("Europe/Moscow")).toLocalTime().toString().take(5) }.getOrDefault("")}", fontWeight = FontWeight.SemiBold)
                            Text(a.clientName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeWaitlistScreen(pin: String, extras: SlotellyExtras) {
    var rows by remember { mutableStateOf<List<WaitlistItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(pin, reload) {
        loading = true
        error = ""
        runCatching { extras.waitlist(pin) }.onSuccess { rows = it }.onFailure { error = "Не удалось обновить. Последний список остаётся на экране." }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Ловец окошек", style = MaterialTheme.typography.headlineSmall); Text("Активных: ${rows.size}") }
            TextButton(onClick = { reload++ }) { Text("Обновить") }
        }
        if (loading && rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row { Text(r.clientName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(if (r.status == "offered") "Предложено" else "Ищем") }
                        if (r.phone.isNotBlank()) Text(r.phone)
                        if (r.desiredText.isNotBlank()) Text(r.desiredText)
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeFinanceScreen(pin: String, extras: SlotellyExtras, appointments: List<AppointmentEntity>) {
    var period by remember { mutableStateOf("Месяц") }
    var summary by remember { mutableStateOf<ReportSummary?>(null) }
    var error by remember { mutableStateOf("") }
    val zone = ZoneId.of("Europe/Moscow")
    LaunchedEffect(pin, period) {
        val now = ZonedDateTime.now(zone)
        val range = when (period) {
            "Сегодня" -> now.toLocalDate().atStartOfDay(zone).toInstant() to now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            "Неделя" -> { val m = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong()); m.atStartOfDay(zone).toInstant() to m.plusDays(7).atStartOfDay(zone).toInstant() }
            else -> now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant() to now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant()
        }
        runCatching { extras.report(pin, range.first, range.second) }.onSuccess { summary = it; error = "" }.onFailure { error = "Серверный отчёт временно недоступен" }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Финансы", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Сегодня", "Неделя", "Месяц").forEach { p -> FilterChip(period == p, { period = p }, { Text(p) }) } }
        Spacer(Modifier.height(18.dp))
        Text("${summary?.total?.toInt() ?: 0} ₽", style = MaterialTheme.typography.headlineLarge)
        if (summary != null) {
            Text("Наличные: ${summary!!.cash.toInt()} ₽")
            Text("Карта: ${summary!!.card.toInt()} ₽")
            if (summary!!.other > 0) Text("Другое: ${summary!!.other.toInt()} ₽")
            Text("Оплачено записей: ${summary!!.paidCount}")
            Text("Без оплаты: ${summary!!.unpaidCount}")
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        val pending = appointments.count { it.pending }
        if (pending > 0) { Spacer(Modifier.height(12.dp)); Text("Ждут синхронизации: $pending", style = MaterialTheme.typography.bodySmall) }
    }
}
