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
import kotlinx.coroutines.launch
import ru.slotelly.app.data.AppointmentEntity
import ru.slotelly.app.data.SlotellyRepository
import ru.slotelly.app.sync.PIN_KEY
import ru.slotelly.app.sync.dataStore

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
    var unlocked by remember { mutableStateOf(false) }
    val appts by repo.appointments().collectAsStateWithLifecycle(emptyList())

    if (!unlocked) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Slotelly", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(pin, { pin = it }, label = { Text("PIN мастера") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        context.dataStore.edit { it[PIN_KEY] = pin }
                        unlocked = true
                        try { repo.sync(pin) } catch (_: Exception) { }
                    }
                }) { Text("Открыть") }
            }
        }
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Slotelly · Неделя") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            WeekGrid(appts, Modifier.weight(1f), onCancel = { id -> scope.launch { repo.cancel(id) } })
            LaunchedEffect(Unit) { try { repo.sync(pin) } catch (_: Exception) { } }
        }
    }
}

@Composable
fun WeekGrid(appts: List<AppointmentEntity>, modifier: Modifier = Modifier, onCancel: (String) -> Unit) {
    val zone = java.time.ZoneId.of("Europe/Moscow")
    val today = java.time.LocalDate.now(zone)
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(modifier.fillMaxWidth()) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val day = appts.filter { java.time.Instant.parse(it.startsAt).atZone(zone).toLocalDate() == d }
            Card(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                Column(Modifier.padding(4.dp)) {
                    Text(d.dayOfWeek.name.take(2), style = MaterialTheme.typography.labelSmall)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                    day.forEach { a ->
                        AssistChip(
                            onClick = {},
                            label = { Text(java.time.Instant.parse(a.startsAt).atZone(zone).toLocalTime().toString().take(5) + " " + a.clientName.take(8), maxLines = 2) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = { onCancel(a.id) }, contentPadding = PaddingValues(0.dp)) {
                            Text("×", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
