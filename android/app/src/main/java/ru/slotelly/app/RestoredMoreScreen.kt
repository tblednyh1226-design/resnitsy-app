package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import ru.slotelly.app.data.NativeSettingsExtras

private const val RM_BOOKING = "https://tblednyh1226-design.github.io/resnitsy-app/booking.html"
private const val RM_BOT = "https://t.me/resnicy_tatyana_bot"
private val RM_GSON = Gson()

@Composable
fun RestoredMoreScreen(
    pin: String,
    settingsJson: String,
    syncedAt: Long?,
    onSync: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { NativeSettingsExtras() }
    val settings = remember(settingsJson) { runCatching { JsonParser.parseString(settingsJson).asJsonObject }.getOrElse { JsonObject() } }
    var online by remember(settingsJson) { mutableStateOf(settings.getAsJsonObject("online_booking")?.get("enabled")?.asBoolean ?: true) }
    var section by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var onlineBusy by remember { mutableStateOf(false) }
    val schedule = settings.getAsJsonObject("schedule") ?: JsonObject()
    val weekdays = schedule.getAsJsonArray("weekdays")?.map { it.asInt } ?: emptyList()
    val dayText = weekdays.joinToString(", ") { rmDay(it) }.ifBlank { "не задано" }
    val start = schedule.get("start")?.asString ?: "10:00"
    val end = schedule.get("end")?.asString ?: "22:30"
    val syncText = syncedAt?.let {
        val m = ((System.currentTimeMillis() - it).coerceAtLeast(0) / 60000)
        if (m < 1) "только что" else "$m мин назад"
    } ?: "ещё не было"

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Онлайн-запись", fontWeight = FontWeight.SemiBold)
                    Text(if (online) "Клиенты могут записываться" else "Запись для клиентов закрыта", style = MaterialTheme.typography.bodySmall)
                }
                Switch(online, onCheckedChange = { enabled ->
                    online = enabled
                    onlineBusy = true
                    scope.launch {
                        runCatching { api.setOnlineBooking(pin, enabled) }
                            .onSuccess { onSettingsChanged() }
                            .onFailure { online = !enabled }
                        onlineBusy = false
                    }
                }, enabled = !onlineBusy)
            }
        }
        RMCard("Режим работы", "$dayText · $start–$end") { section = "work" }
        RMCard("Уведомления", "Автоотправка, напоминания, каналы, шаблоны, журнал") { section = "notifications" }
        RMCard("Техподдержка", "Помощь, ошибки и обращения") { section = "support" }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ссылка онлайн-записи", fontWeight = FontWeight.SemiBold)
                Text(RM_BOOKING, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Slotelly", RM_BOOKING)); copied = true
                    }, modifier = Modifier.weight(1f)) { Text(if (copied) "Скопировано ✓" else "Копировать") }
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Записаться онлайн: $RM_BOOKING") }
                        context.startActivity(Intent.createChooser(i, "Поделиться"))
                    }, modifier = Modifier.weight(1f)) { Text("Поделиться") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Telegram-бот", fontWeight = FontWeight.SemiBold)
                Text("@resnicy_tatyana_bot")
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RM_BOT))) }) { Text("Открыть бота") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Синхронизация", fontWeight = FontWeight.SemiBold)
                Text("Последняя: $syncText", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать сейчас") }
            }
        }
    }

    when (section) {
        "work" -> RMWorkOverlay(pin, api, settings, { section = null }) { onSettingsChanged() }
        "notifications" -> RMNotificationsOverlay(pin, api) { section = null }
        "support" -> RMSupportOverlay { section = null }
    }
}

@Composable
private fun RMCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun rmDay(v: Int) = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").getOrElse(v) { v.toString() }

@Composable
private fun RMWorkOverlay(pin: String, api: NativeSettingsExtras, settings: JsonObject, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val schedule = settings.getAsJsonObject("schedule") ?: JsonObject()
    var start by remember { mutableStateOf(schedule.get("start")?.asString ?: "10:00") }
    var end by remember { mutableStateOf(schedule.get("end")?.asString ?: "22:30") }
    var weekdays by remember { mutableStateOf(schedule.getAsJsonArray("weekdays")?.map { it.asInt }?.toSet() ?: emptySet()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    FullOverlay(onClose) {
        Text("Режим работы", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Базовый график", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { d -> FilterChip(selected = d in weekdays, onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d }, label = { Text(rmDay(d)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("Начало") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("Конец") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                saving = true
                scope.launch {
                    @Suppress("UNCHECKED_CAST")
                    val base = runCatching { RM_GSON.fromJson(schedule, MutableMap::class.java) as MutableMap<String, Any?> }.getOrElse { mutableMapOf() }
                    base["start"] = start; base["end"] = end; base["weekdays"] = weekdays.sorted()
                    runCatching { api.patchSettings(pin, mapOf("schedule" to base)) }
                        .onSuccess { onSaved() }
                        .onFailure { error = it.message ?: "Ошибка сохранения" }
                    saving = false
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Сохраняю…" else "Сохранить базовый график") }
            ScheduleToolsSection(pin, api)
        }
    }
}

@Composable
private fun RMNotificationsOverlay(pin: String, api: NativeSettingsExtras, onClose: () -> Unit) {
    var root by remember { mutableStateOf<JsonObject?>(null) }
    var journal by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { api.notificationsGet(pin) }.onSuccess { root = it }.onFailure { error = it.message ?: "Не удалось загрузить уведомления" }
        runCatching { api.notificationsJournal(pin) }.onSuccess { journal = it.getAsJsonArray("rows")?.map { x -> x.asJsonObject } ?: emptyList() }
        loading = false
    }
    FullOverlay(onClose) {
        Text("Уведомления", style = MaterialTheme.typography.headlineSmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        root?.let { RMNotificationBody(pin, api, it, journal) }
    }
}

@Composable
private fun ColumnScope.RMNotificationBody(pin: String, api: NativeSettingsExtras, root: JsonObject, journal: List<JsonObject>) {
    val scope = rememberCoroutineScope()
    val n = root.getAsJsonObject("settings") ?: JsonObject()
    val status = root.getAsJsonObject("status") ?: JsonObject()
    val ch = n.getAsJsonObject("channels") ?: JsonObject()
    val mp = n.getAsJsonObject("master_push") ?: JsonObject()
    val tpl = n.getAsJsonObject("templates") ?: JsonObject()
    var enabled by remember { mutableStateOf(n.get("enabled")?.asBoolean ?: true) }
    var confirmation by remember { mutableStateOf(n.get("confirmation_enabled")?.asBoolean ?: true) }
    var reschedule by remember { mutableStateOf(n.get("reschedule_enabled")?.asBoolean ?: true) }
    var cancellation by remember { mutableStateOf(n.get("cancellation_enabled")?.asBoolean ?: true) }
    var dayBefore by remember { mutableStateOf(n.get("reminder_day_before")?.asBoolean ?: true) }
    var reminderTime by remember { mutableStateOf(n.get("reminder_day_before_time")?.asString ?: "19:00") }
    var hoursOn by remember { mutableStateOf(n.get("reminder_hours_before")?.asBoolean ?: false) }
    var hours by remember { mutableStateOf((n.get("reminder_hours")?.asInt ?: 3).toString()) }
    var telegram by remember { mutableStateOf(ch.get("telegram")?.asBoolean ?: true) }
    var whatsapp by remember { mutableStateOf(ch.get("whatsapp")?.asBoolean ?: false) }
    var vk by remember { mutableStateOf(ch.get("vk")?.asBoolean ?: false) }
    var max by remember { mutableStateOf(ch.get("max")?.asBoolean ?: false) }
    var masterEnabled by remember { mutableStateOf(mp.get("enabled")?.asBoolean ?: true) }
    var masterNew by remember { mutableStateOf(mp.get("new_booking")?.asBoolean ?: true) }
    var masterChanged by remember { mutableStateOf(mp.get("booking_changed")?.asBoolean ?: true) }
    var masterCancelled by remember { mutableStateOf(mp.get("booking_cancelled")?.asBoolean ?: true) }
    var waitlistNew by remember { mutableStateOf(mp.get("waitlist_new")?.asBoolean ?: true) }
    var waitlistResponse by remember { mutableStateOf(mp.get("waitlist_response")?.asBoolean ?: true) }
    var tConfirm by remember { mutableStateOf(tpl.get("confirmation")?.asString ?: "") }
    var tReminder by remember { mutableStateOf(tpl.get("reminder")?.asString ?: "") }
    var tMove by remember { mutableStateOf(tpl.get("reschedule")?.asString ?: "") }
    var tCancel by remember { mutableStateOf(tpl.get("cancellation")?.asString ?: "") }
    var message by remember { mutableStateOf("") }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bot = status.getAsJsonObject("telegram_bot")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text("Telegram-бот", fontWeight = FontWeight.SemiBold); Text(if (bot != null) "Подключён @${bot.get("bot_username")?.asString ?: ""}" else "Не подключён") } }
        RMSwitch("Автоуведомления клиентам", enabled) { enabled = it }
        RMSwitch("Подтверждение записи", confirmation) { confirmation = it }
        RMSwitch("Перенос записи", reschedule) { reschedule = it }
        RMSwitch("Отмена записи", cancellation) { cancellation = it }
        RMSwitch("Напоминание накануне", dayBefore) { dayBefore = it }
        OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Время напоминания") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        RMSwitch("Дополнительное напоминание за несколько часов", hoursOn) { hoursOn = it }
        OutlinedTextField(hours, { hours = it }, label = { Text("За сколько часов") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Каналы клиенту", style = MaterialTheme.typography.titleMedium)
        RMSwitch("Telegram", telegram) { telegram = it }; RMSwitch("WhatsApp", whatsapp) { whatsapp = it }; RMSwitch("VK", vk) { vk = it }; RMSwitch("MAX", max) { max = it }
        Text("Уведомления мастеру", style = MaterialTheme.typography.titleMedium)
        RMSwitch("Включены", masterEnabled) { masterEnabled = it }; RMSwitch("Новая запись", masterNew) { masterNew = it }; RMSwitch("Изменение записи", masterChanged) { masterChanged = it }; RMSwitch("Отмена записи", masterCancelled) { masterCancelled = it }; RMSwitch("Новый запрос Ловца", waitlistNew) { waitlistNew = it }; RMSwitch("Ответ по Ловцу", waitlistResponse) { waitlistResponse = it }
        Text("Шаблоны", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(tConfirm, { tConfirm = it }, label = { Text("Подтверждение") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(tReminder, { tReminder = it }, label = { Text("Напоминание") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(tMove, { tMove = it }, label = { Text("Перенос") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(tCancel, { tCancel = it }, label = { Text("Отмена") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Text("Последние отправки", style = MaterialTheme.typography.titleMedium)
        if (journal.isEmpty()) Text("Журнал пока пуст", style = MaterialTheme.typography.bodySmall)
        journal.take(20).forEach { r ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("${r.get("template_type")?.asString ?: "Сообщение"} · ${r.get("delivery_status")?.asString ?: ""}", fontWeight = FontWeight.SemiBold); Text(r.get("message_text")?.asString ?: "", maxLines = 2, style = MaterialTheme.typography.bodySmall) } }
        }
    }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        scope.launch {
            val settings = mapOf<String, Any?>(
                "enabled" to enabled, "confirmation_enabled" to confirmation, "reschedule_enabled" to reschedule,
                "cancellation_enabled" to cancellation, "reminder_day_before" to dayBefore,
                "reminder_day_before_time" to reminderTime, "reminder_hours_before" to hoursOn,
                "reminder_hours" to (hours.toIntOrNull() ?: 3),
                "channels" to mapOf("telegram" to telegram, "whatsapp" to whatsapp, "vk" to vk, "max" to max),
                "master_push" to mapOf("enabled" to masterEnabled, "new_booking" to masterNew, "booking_changed" to masterChanged, "booking_cancelled" to masterCancelled, "waitlist_new" to waitlistNew, "waitlist_response" to waitlistResponse),
                "templates" to mapOf("confirmation" to tConfirm, "reminder" to tReminder, "reschedule" to tMove, "cancellation" to tCancel)
            )
            runCatching { api.notificationsSave(pin, settings) }.onSuccess { message = "Сохранено ✓" }.onFailure { message = it.message ?: "Ошибка сохранения" }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить уведомления") }
}

@Composable
private fun RMSwitch(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) { Text(title, modifier = Modifier.weight(1f)); Switch(value, onCheckedChange = onChange) }
}

@Composable
private fun RMSupportOverlay(onClose: () -> Unit) {
    FullOverlay(onClose) {
        Text("Техподдержка", style = MaterialTheme.typography.headlineSmall)
        Text("Написать в поддержку", fontWeight = FontWeight.SemiBold)
        Text("Вопрос или помощь по работе Slotelly", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("Сообщить об ошибке", fontWeight = FontWeight.SemiBold)
        Text("Описание, шаги и скриншот", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("Мои обращения", fontWeight = FontWeight.SemiBold)
        Text("История и статусы обращений будут храниться здесь.", style = MaterialTheme.typography.bodySmall)
    }
}
