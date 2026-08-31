package ru.slotelly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.slotelly.app.data.*
import ru.slotelly.app.sync.PIN_KEY
import ru.slotelly.app.sync.dataStore
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
private val RU = Locale("ru", "RU")

enum class RootTab { CALENDAR, CLIENTS, FINANCE }
enum class CalendarMode { WEEK, DAY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SlotellyRepository((application as SlotellyApp).db)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8F4F68))) {
                SlotellyRoot(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotellyRoot(repo: SlotellyRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(RootTab.CALENDAR) }
    var selectedAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var editAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showNewClient by remember { mutableStateOf(false) }
    var paymentAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }

    val appts by repo.appointments().collectAsStateWithLifecycle(emptyList())
    val clients by repo.clients().collectAsStateWithLifecycle(emptyList())
    val services by repo.services().collectAsStateWithLifecycle(emptyList())
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
                    val age = state?.syncedAt?.let { System.currentTimeMillis() - it }
                    Text(if (age != null && age < 120_000) "✓" else "○", modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { scope.launch { runCatching { repo.sync(pin) } } }) { Text("Обновить") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == RootTab.CALENDAR, onClick = { tab = RootTab.CALENDAR }, icon = { Text("▦") }, label = { Text("Календарь") })
                NavigationBarItem(selected = tab == RootTab.CLIENTS, onClick = { tab = RootTab.CLIENTS }, icon = { Text("◉") }, label = { Text("Клиенты") })
                NavigationBarItem(selected = tab == RootTab.FINANCE, onClick = { tab = RootTab.FINANCE }, icon = { Text("₽") }, label = { Text("Финансы") })
            }
        },
        floatingActionButton = {
            if (tab == RootTab.CALENDAR) FloatingActionButton(onClick = { editAppointment = null; showEditor = true }) { Text("+") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                RootTab.CALENDAR -> CalendarScreen(appts, onOpen = { selectedAppointment = it })
                RootTab.CLIENTS -> ClientsScreen(clients, onNew = { showNewClient = true })
                RootTab.FINANCE -> FinanceScreen(appts)
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
        AppointmentEditor(
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
fun CalendarScreen(appts: List<AppointmentEntity>, onOpen: (AppointmentEntity) -> Unit) {
    var mode by remember { mutableStateOf(CalendarMode.WEEK) }
    var focus by remember { mutableStateOf(LocalDate.now(ZONE)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { focus = focus.minusDays(if (mode == CalendarMode.WEEK) 7 else 1) }) { Text("‹") }
            Text(
                if (mode == CalendarMode.WEEK) weekLabel(focus) else focus.format(DateTimeFormatter.ofPattern("dd.MM (EEE)", RU)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { focus = LocalDate.now(ZONE) }) { Text("Сегодня") }
            TextButton(onClick = { focus = focus.plusDays(if (mode == CalendarMode.WEEK) 7 else 1) }) { Text("›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == CalendarMode.WEEK, onClick = { mode = CalendarMode.WEEK }, label = { Text("Неделя") })
            FilterChip(selected = mode == CalendarMode.DAY, onClick = { mode = CalendarMode.DAY }, label = { Text("День") })
        }
        Spacer(Modifier.height(6.dp))
        if (mode == CalendarMode.WEEK) WeekGrid(appts, focus, Modifier.weight(1f), onOpen)
        else DayView(appts, focus, Modifier.weight(1f), onOpen)
    }
}

@Composable
fun WeekGrid(appts: List<AppointmentEntity>, focus: LocalDate, modifier: Modifier = Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val monday = focus.minusDays((focus.dayOfWeek.value - 1).toLong())
    Row(modifier.fillMaxWidth()) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val day = appts.filter { localDate(it.startsAt) == d }.sortedBy { it.startsAt }
            Card(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp)) {
                    Text(d.format(DateTimeFormatter.ofPattern("EE", RU)).replace(".", ""), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    day.take(6).forEach { a ->
                        Surface(
                            tonalElevation = if (a.pending) 4.dp else 1.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { onOpen(a) }
                        ) {
                            Column(Modifier.padding(2.dp)) {
                                Text(time(a.startsAt), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(a.clientName, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (day.size > 6) Text("+${day.size - 6}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun DayView(appts: List<AppointmentEntity>, date: LocalDate, modifier: Modifier = Modifier, onOpen: (AppointmentEntity) -> Unit) {
    val rows = appts.filter { localDate(it.startsAt) == date }.sortedBy { it.startsAt }
    if (rows.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Записей нет") }
    } else {
        LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(a) }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(time(a.startsAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.clientName, fontWeight = FontWeight.SemiBold)
                            Text(serviceNames(a.servicesJson), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (a.pending) Text("○")
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentDialog(
    appointment: AppointmentEntity,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onUnpaid: () -> Unit,
    onPayment: () -> Unit
) {
    FullOverlay(onClose) {
        Text(appointment.clientName, style = MaterialTheme.typography.headlineSmall)
        Text("${dateLabel(appointment.startsAt)} · ${time(appointment.startsAt)}–${time(appointment.endsAt)}")
        Spacer(Modifier.height(12.dp))
        Text(serviceNames(appointment.servicesJson), style = MaterialTheme.typography.titleMedium)
        Text("Стоимость: ${serviceTotal(appointment.servicesJson).toInt()} ₽")
        if (appointment.comment.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Комментарий: ${appointment.comment}") }
        if (appointment.pending) { Spacer(Modifier.height(8.dp)); Text("Изменение сохранено на телефоне и ждёт синхронизации") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onPayment, modifier = Modifier.fillMaxWidth()) { Text("Провести оплату") }
        OutlinedButton(onClick = onUnpaid, modifier = Modifier.fillMaxWidth()) { Text("Завершить без оплаты") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Редактировать") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отменить запись") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentEditor(
    existing: AppointmentEntity?,
    clients: List<ClientEntity>,
    services: List<ServiceEntity>,
    onClose: () -> Unit,
    onSave: (ClientEntity, ServiceEntity, String, Double, Int, String) -> Unit
) {
    var client by remember(existing, clients) { mutableStateOf(existing?.clientId?.let { id -> clients.find { it.id == id } } ?: clients.firstOrNull()) }
    var service by remember(existing, services) {
        val sid = existing?.servicesJson?.let { firstServiceId(it) }
        mutableStateOf(services.find { it.id == sid } ?: services.firstOrNull())
    }
    var date by remember(existing) { mutableStateOf(existing?.startsAt?.let { localDate(it).toString() } ?: LocalDate.now(ZONE).toString()) }
    var tm by remember(existing) { mutableStateOf(existing?.startsAt?.let { time(it) } ?: "10:00") }
    var price by remember(existing, service) { mutableStateOf((existing?.servicesJson?.let { firstServicePrice(it) } ?: service?.price ?: 0.0).toInt().toString()) }
    var duration by remember(existing, service) { mutableStateOf((existing?.servicesJson?.let { firstServiceDuration(it) } ?: service?.duration ?: 60).toString()) }
    var comment by remember(existing) { mutableStateOf(existing?.comment.orEmpty()) }
    var clientMenu by remember { mutableStateOf(false) }
    var serviceMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    FullOverlay(onClose) {
        Text(if (existing == null) "Новая запись" else "Редактирование", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(expanded = clientMenu, onExpandedChange = { clientMenu = !clientMenu }) {
            OutlinedTextField(client?.name.orEmpty(), {}, readOnly = true, label = { Text("Клиент") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = clientMenu, onDismissRequest = { clientMenu = false }) {
                clients.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { client = c; clientMenu = false }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = serviceMenu, onExpandedChange = { serviceMenu = !serviceMenu }) {
            OutlinedTextField(service?.name.orEmpty(), {}, readOnly = true, label = { Text("Услуга") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(serviceMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = serviceMenu, onDismissRequest = { serviceMenu = false }) {
                services.forEach { s -> DropdownMenuItem(text = { Text("${s.name} · ${s.price.toInt()} ₽") }, onClick = { service = s; price = s.price.toInt().toString(); duration = s.duration.toString(); serviceMenu = false }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Дата ГГГГ-ММ-ДД") }, singleLine = true, modifier = Modifier.weight(1.4f))
            OutlinedTextField(tm, { tm = it }, label = { Text("Время") }, singleLine = true, modifier = Modifier.weight(0.8f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Цена") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Минут") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        Button(onClick = {
            val c = client; val s = service
            try {
                if (c == null || s == null) error = "Выберите клиента и услугу"
                else {
                    val ldt = LocalDateTime.parse("${date}T${tm}")
                    val iso = ldt.atZone(ZONE).toInstant().toString()
                    val p = price.toDoubleOrNull() ?: s.price
                    val dur = duration.toIntOrNull() ?: s.duration
                    onSave(c, s, iso, p, dur, comment)
                }
            } catch (_: Exception) { error = "Проверьте дату и время" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
    }
}

@Composable
fun PaymentDialog(a: AppointmentEntity, onClose: () -> Unit, onSave: (Double, Double, Double) -> Unit) {
    val total = serviceTotal(a.servicesJson)
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf(total.toInt().toString()) }
    var other by remember { mutableStateOf("") }
    FullOverlay(onClose) {
        Text("Оплата", style = MaterialTheme.typography.headlineSmall)
        Text(a.clientName)
        Text("К оплате: ${total.toInt()} ₽")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(cash, { cash = it.filter(Char::isDigit) }, label = { Text("Наличные") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(card, { card = it.filter(Char::isDigit) }, label = { Text("Карта") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(other, { other = it.filter(Char::isDigit) }, label = { Text("Другое") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Button(onClick = { onSave(cash.toDoubleOrNull() ?: 0.0, card.toDoubleOrNull() ?: 0.0, other.toDoubleOrNull() ?: 0.0) }, modifier = Modifier.fillMaxWidth()) { Text("Провести") }
    }
}

@Composable
fun ClientsScreen(clients: List<ClientEntity>, onNew: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val rows = remember(clients, query) { clients.filter { it.name.contains(query, true) || it.phone.contains(query) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = onNew) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        if (c.phone.isNotBlank()) Text(c.phone)
                        val last = serviceNames(c.lastServicesJson)
                        if (last.isNotBlank()) Text("Последнее: $last", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun NewClientDialog(onClose: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+7") }
    var messenger by remember { mutableStateOf("telegram") }
    FullOverlay(onClose) {
        Text("Новый клиент", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(messenger, { messenger = it }, label = { Text("Мессенджер") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Button(onClick = { if (name.isNotBlank() && phone.length >= 10) onCreate(name, phone, messenger) }, modifier = Modifier.fillMaxWidth()) { Text("Создать") }
    }
}

@Composable
fun FinanceScreen(appts: List<AppointmentEntity>) {
    val paid = appts.filter { it.status == "completed_paid" }
    val total = paid.sumOf { paymentTotal(it.paymentsJson) }
    val cash = paid.sumOf { paymentPart(it.paymentsJson, "cash") }
    val card = paid.sumOf { paymentPart(it.paymentsJson, "card") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Финансы", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Оплачено: ${total.toInt()} ₽", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Наличные: ${cash.toInt()} ₽")
        Text("Карта: ${card.toInt()} ₽")
        Text("Записей оплачено: ${paid.size}")
        Spacer(Modifier.height(16.dp))
        Text("Сейчас показаны данные, уже сохранённые на телефоне. Серверный отчёт по выбранному периоду добавлю следующим слоем.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FullOverlay(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onClose) { Text("Закрыть") } }
                content()
            }
        }
    }
}

private fun localDate(iso: String): LocalDate = Instant.parse(iso).atZone(ZONE).toLocalDate()
private fun time(iso: String): String = Instant.parse(iso).atZone(ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
private fun dateLabel(iso: String): String = Instant.parse(iso).atZone(ZONE).format(DateTimeFormatter.ofPattern("dd.MM (EEE)", RU)).replace(".", ".")
private fun weekLabel(focus: LocalDate): String {
    val m = focus.minusDays((focus.dayOfWeek.value - 1).toLong())
    val s = m.plusDays(6)
    return "${m.format(DateTimeFormatter.ofPattern("dd.MM"))}–${s.format(DateTimeFormatter.ofPattern("dd.MM"))}"
}
private fun serviceNames(json: String): String = runCatching {
    JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
        val o = e.asJsonObject
        o["name"]?.takeUnless { it.isJsonNull }?.asString ?: o["service_name_snapshot"]?.takeUnless { it.isJsonNull }?.asString
    }.joinToString(" + ")
}.getOrDefault("")
private fun serviceTotal(json: String): Double = runCatching {
    JsonParser.parseString(json).asJsonArray.sumOf { e ->
        val o = e.asJsonObject
        o["price"]?.asDouble ?: o["actual_price"]?.asDouble ?: 0.0
    }
}.getOrDefault(0.0)
private fun firstServiceId(json: String): String? = runCatching { JsonParser.parseString(json).asJsonArray.firstOrNull()?.asJsonObject?.get("service_id")?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun firstServicePrice(json: String): Double? = runCatching { JsonParser.parseString(json).asJsonArray.firstOrNull()?.asJsonObject?.let { it["price"]?.asDouble ?: it["actual_price"]?.asDouble } }.getOrNull()
private fun firstServiceDuration(json: String): Int? = runCatching { JsonParser.parseString(json).asJsonArray.firstOrNull()?.asJsonObject?.let { it["duration"]?.asInt ?: it["duration_minutes"]?.asInt } }.getOrNull()
private fun paymentTotal(json: String): Double = runCatching { JsonParser.parseString(json).asJsonArray.sumOf { it.asJsonObject["total"]?.asDouble ?: it.asJsonObject["total_amount"]?.asDouble ?: 0.0 } }.getOrDefault(0.0)
private fun paymentPart(json: String, key: String): Double = runCatching { JsonParser.parseString(json).asJsonArray.sumOf { it.asJsonObject[key]?.asDouble ?: it.asJsonObject["${key}_amount"]?.asDouble ?: 0.0 } }.getOrDefault(0.0)
