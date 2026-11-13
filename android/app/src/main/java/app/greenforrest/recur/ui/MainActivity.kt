package app.greenforrest.recur.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.greenforrest.recur.RecurApp
import app.greenforrest.recur.core.Subscription
import app.greenforrest.recur.notify.Reminders
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val repo = (application as RecurApp).repo
    setContent { RecurTheme { RecurRoot(repo) } }
  }

  override fun onResume() {
    super.onResume()
    // Opening the app is a good moment to catch anything the worker has not posted yet.
    Reminders.postDue(this)
  }
}

private val Green = Color(0xFF1D8F5F)

@Composable
fun RecurTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val scheme = if (dark) darkColorScheme(primary = Color(0xFF5CC897), secondary = Color(0xFF8FD3B4), primaryContainer = Color(0xFF1F5A40), onPrimaryContainer = Color(0xFFCFF3E0))
  else lightColorScheme(primary = Green, secondary = Color(0xFF2F6B52), primaryContainer = Color(0xFFC8F0DC), onPrimaryContainer = Color(0xFF0B3B26))
  MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurRoot(repo: app.greenforrest.recur.data.Repository) {
  val store by repo.store.collectAsStateWithLifecycle()
  val ctx = LocalContext.current
  val prefs = remember { ctx.getSharedPreferences("recur", Context.MODE_PRIVATE) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var editing by remember { mutableStateOf<EditTarget?>(null) }
  var bulkIds by remember { mutableStateOf<Set<String>?>(null) }
  var showTour by remember { mutableStateOf(!prefs.getBoolean("onboarded", false)) }
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    if (it) Reminders.postDue(ctx)
  }

  fun deleteWithUndo(ids: Set<String>) {
    repo.softDelete(ids, true)
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      val r = snackbar.showSnackbar(
        if (ids.size == 1) "Deleted" else "Deleted ${ids.size}", actionLabel = "Undo", duration = SnackbarDuration.Long,
      )
      if (r == SnackbarResult.ActionPerformed) repo.softDelete(ids, false)
    }
  }

  val target = editing
  if (target != null) {
    Editor(
      target = target, currency = store.currency,
      onSave = { sub -> repo.upsert(listOf(sub)); editing = null },
      onDelete = { id -> editing = null; deleteWithUndo(setOf(id)) },
      onClose = { editing = null },
    )
    return
  }
  val bulk = bulkIds
  if (bulk != null) {
    BulkEditor(
      subs = store.subscriptions.filter { it.id in bulk }, currency = store.currency,
      onSave = { repo.upsert(it); bulkIds = null },
      onClose = { bulkIds = null },
    )
    return
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = {
      if (tab != 2) FloatingActionButton(onClick = { editing = EditTarget.New }) {
        Icon(Icons.Filled.Add, contentDescription = "Add subscription")
      }
    },
    bottomBar = {
      NavigationBar {
        listOf(
          Triple("Overview", Icons.Outlined.Home, 0),
          Triple("Subscriptions", Icons.AutoMirrored.Outlined.List, 1),
          Triple("Settings", Icons.Outlined.Settings, 2),
        ).forEach { (label, icon, i) ->
          NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
        }
      }
    },
  ) { pad ->
    val m = Modifier.padding(pad)
    when (tab) {
      0 -> OverviewScreen(store, m, onOpen = { editing = EditTarget.Existing(it) })
      1 -> ListScreen(
        store, m,
        onOpen = { editing = EditTarget.Existing(it) },
        onDelete = { deleteWithUndo(it) },
        onBulkEdit = { bulkIds = it },
      )
      else -> SettingsScreen(
        store, repo, m,
        onAskNotify = {
          if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onShowTour = { showTour = true },
        onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
      )
    }
  }

  if (showTour) Tour(onDone = {
    showTour = false
    prefs.edit().putBoolean("onboarded", true).apply()
    if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
  })
}

sealed interface EditTarget {
  data object New : EditTarget
  data class Existing(val sub: Subscription) : EditTarget
}

@Composable
private fun Tour(onDone: () -> Unit) {
  val pages = listOf(
    "Everything that renews" to "Subscriptions, data bundles, free trials, yearly bills and anything with an end date, in one list.",
    "What it really costs" to "The overview shows what actually leaves your account this month, next to the smoothed monthly average. A contract renewal fee counts in the month it lands.",
    "Reminded in time" to "Recur reminds you before a charge, before a trial turns paid and well before anything with an end date runs out, so you can renew, upgrade or switch on your terms.",
    "Yours, on this phone" to "Data stays on the device. Export JSON from Settings to move it to the web app or back it up.",
  )
  var i by remember { mutableIntStateOf(0) }
  AlertDialog(
    onDismissRequest = {},
    title = { Text(pages[i].first) },
    text = { Text(pages[i].second) },
    confirmButton = {
      TextButton(onClick = { if (i < pages.lastIndex) i++ else onDone() }) {
        Text(if (i < pages.lastIndex) "Next (${i + 1}/${pages.size})" else "Get started")
      }
    },
    dismissButton = { if (i < pages.lastIndex) TextButton(onClick = onDone) { Text("Skip") } },
  )
}
