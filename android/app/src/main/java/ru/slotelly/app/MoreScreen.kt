package ru.slotelly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val BOOKING_LINK = "https://tblednyh1226-design.github.io/resnitsy-app/booking.html"
private const val BOT_LINK = "https://t.me/resnicy_tatyana_bot"

@Composable
fun MoreScreen(
    syncedAt: Long?,
    onSync: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val ageText = syncedAt?.let {
        val sec = ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 1000L)
        when {
            sec < 60 -> "только что"
            sec < 3600 -> "${sec / 60} мин назад"
            else -> "${sec / 3600} ч назад"
        }
    } ?: "ещё не синхронизировано"

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ещё", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Онлайн-запись для клиентов", style = MaterialTheme.typography.titleMedium)
                Text(BOOKING_LINK, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Slotelly", BOOKING_LINK))
                        copied = true
                    }, modifier = Modifier.weight(1f)) { Text(if (copied) "Скопировано ✓" else "Копировать") }
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Записаться онлайн: $BOOKING_LINK")
                        }
                        context.startActivity(Intent.createChooser(i, "Поделиться ссылкой"))
                    }, modifier = Modifier.weight(1f)) { Text("Поделиться") }
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BOOKING_LINK)))
                }) { Text("Открыть клиентскую страницу") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Telegram-бот", style = MaterialTheme.typography.titleMedium)
                Text("@resnicy_tatyana_bot")
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BOT_LINK))) }) {
                    Text("Открыть бота")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Синхронизация", style = MaterialTheme.typography.titleMedium)
                Text("Последняя: $ageText")
                Text("Календарь и действия работают из памяти телефона. Сервер обновляется в фоне.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать сейчас") }
            }
        }
    }
}
