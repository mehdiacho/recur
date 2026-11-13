package app.greenforrest.recur.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.greenforrest.recur.core.Dates
import app.greenforrest.recur.core.Store
import app.greenforrest.recur.data.Repository
import app.greenforrest.recur.notify.Reminders

private val CURRENCIES = listOf("BWP", "ZAR", "USD", "EUR", "GBP")

@Composable
fun SettingsScreen(
  store: Store, repo: Repository, modifier: Modifier,
  onAskNotify: () -> Unit, onShowTour: () -> Unit, onMessage: (String) -> Unit,
) {
  val ctx = LocalContext.current
  var canNotify by remember { mutableStateOf(Reminders.canNotify(ctx)) }
  LaunchedEffect(store) { canNotify = Reminders.canNotify(ctx) }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) runCatching {
      ctx.contentResolver.openOutputStream(uri)!!.use { it.write(repo.exportJson().toByteArray()) }
    }.onSuccess { onMessage("Exported") }.onFailure { onMessage("Export failed: ${it.message}") }
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) runCatching {
      val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
      repo.import(text)
    }.onSuccess { onMessage("Imported $it") }.onFailure { onMessage("That file isn't a Recur export") }
  }
  val deleted = store.subscriptions.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Settings", style = MaterialTheme.typography.titleLarge)
    Dropdown(store.currency, CURRENCIES, Modifier.fillMaxWidth(), "Currency for new items") { c ->
      repo.update { it.copy(currency = c) }
    }
    OutlinedCard {
      Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Reminders")
          Text(if (canNotify) "Notifications are on." else "Notifications are off, so reminders only show in the app.",
            style = MaterialTheme.typography.bodySmall)
        }
        if (!canNotify) TextButton(onClick = onAskNotify) { Text("Turn on") }
      }
    }
    Text("Your data", style = MaterialTheme.typography.titleSmall)
    Text("Everything is stored on this phone. The export is the same JSON the web app uses, so you can move between them.",
      style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { exportLauncher.launch("recur-${Dates.today()}.json") }) { Text("Export JSON") }
      OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
    }
    if (deleted.isNotEmpty()) {
      Text("Recently deleted", style = MaterialTheme.typography.titleSmall)
      deleted.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Tile(s.name, 32)
          Spacer(Modifier.width(8.dp))
          Text(s.name, Modifier.weight(1f))
          TextButton(onClick = { repo.softDelete(listOf(s.id), false) }) { Text("Restore") }
        }
      }
    }
    TextButton(onClick = onShowTour) { Text("Show the tour again") }
    Text("Recur 0.1 · part of SmartSpend", style = MaterialTheme.typography.labelSmall)
  }
}
