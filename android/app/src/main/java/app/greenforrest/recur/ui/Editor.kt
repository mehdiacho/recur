package app.greenforrest.recur.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.greenforrest.recur.core.*

private val LEADS = listOf(0, 1, 3, 7, 14, 30, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(
  target: EditTarget, currency: String,
  onSave: (Subscription) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit,
) {
  val existing = (target as? EditTarget.Existing)?.sub
  // Step 1 (new only): pick a preset or start blank. Step 2: the form.
  var picked by remember { mutableStateOf(existing != null) }
  val today = remember { Dates.today() }
  var draft by remember {
    mutableStateOf(existing ?: Subscription(id = newId(), name = "", amount = 0.0, currency = currency, anchorDate = today))
  }
  var amountText by remember { mutableStateOf(existing?.amount?.let { fmtNum(it) } ?: "") }
  var renewalText by remember { mutableStateOf(existing?.renewalCost?.let { fmtNum(it) } ?: "") }
  var everyText by remember { mutableStateOf((existing?.cycleEvery ?: 1).toString()) }
  var showMore by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  BackHandler { if (!picked || existing != null) onClose() else picked = false }

  fun applyKind(k: String) {
    val d = defaultsFor(k)
    draft = draft.copy(
      kind = k, remindDays = d.remindDays, remindCharges = d.remindCharges, autoRenews = d.autoRenews,
      endDate = if (k == "contract") draft.endDate ?: Dates.addMonths(today, 24) else if (k == "trial") null else draft.endDate,
    )
  }

  Scaffold(topBar = {
    TopAppBar(
      title = { Text(if (existing != null) "Edit" else if (!picked) "Add · step 1 of 2" else "Add · step 2 of 2") },
      navigationIcon = { IconButton(onClick = { if (!picked || existing != null) onClose() else picked = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
      actions = {
        if (picked) TextButton(
          enabled = draft.name.isNotBlank() && (amountText.toDoubleOrNull() ?: -1.0) >= 0,
          onClick = {
            onSave(draft.copy(
              name = draft.name.trim(),
              amount = amountText.toDoubleOrNull() ?: 0.0,
              renewalCost = renewalText.toDoubleOrNull(),
              cycleEvery = everyText.toIntOrNull()?.coerceIn(1, 365) ?: 1,
            ))
          },
        ) { Text("Save") }
      },
    )
  }) { pad ->
    if (!picked) {
      Column(Modifier.padding(pad).padding(16.dp)) {
        OutlinedButton(onClick = { picked = true }, Modifier.fillMaxWidth()) { Text("Something else (start blank)") }
        Spacer(Modifier.height(12.dp))
        Text("Or pick one. You add the price; it varies too much to guess.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(GridCells.Adaptive(100.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(PRESETS) { p ->
            Column(
              Modifier.clickable {
                draft = draft.copy(name = p.name, category = p.category, cycleUnit = p.cycleUnit, url = p.url)
                applyKind(p.kind)
                picked = true
              }.padding(8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Tile(p.name, 44)
              Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 2)
            }
          }
        }
      }
      return@Scaffold
    }

    Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        KINDS.forEachIndexed { i, k ->
          SegmentedButton(draft.kind == k, { applyKind(k) }, SegmentedButtonDefaults.itemShape(i, KINDS.size)) {
            Text(k.replaceFirstChar { it.uppercase() })
          }
        }
      }
      OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f),
          label = { Text(if (draft.kind == "trial") "Price after trial" else "Amount") },
          prefix = { Text(draft.currency + " ") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Every")
        OutlinedTextField(everyText, { everyText = it.filter(Char::isDigit).take(3) }, Modifier.width(72.dp), singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Dropdown(draft.cycleUnit, CYCLE_UNITS, Modifier.weight(1f)) { draft = draft.copy(cycleUnit = it) }
      }
      DateField(
        if (draft.kind == "trial") "Trial ends (first charge)" else "First or next charge", draft.anchorDate,
      ) { draft = draft.copy(anchorDate = it) }

      if (draft.kind == "contract") {
        OutlinedCard {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contract", fontWeight = FontWeight.SemiBold)
            DateField("Contract ends", draft.endDate ?: Dates.addMonths(today, 24)) { draft = draft.copy(endDate = it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Keeps billing after it ends", Modifier.weight(1f))
              Switch(draft.autoRenews, { draft = draft.copy(autoRenews = it) })
            }
            OutlinedTextField(
              renewalText, { renewalText = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(),
              label = { Text("Renewal or upgrade cost (one-off)") }, prefix = { Text(draft.currency + " ") }, singleLine = true,
              supportingText = { Text("Counted in the month the contract ends.") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
          }
        }
      }

      OutlinedCard {
        Column(Modifier.padding(12.dp)) {
          Text("Remind me", fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LEADS.forEach { d ->
              val on = d in draft.remindDays
              FilterChip(on, {
                draft = draft.copy(remindDays = (if (on) draft.remindDays - d else draft.remindDays + d).distinct().sorted())
              }, label = { Text(if (d == 0) "On the day" else "$d d before") })
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Also for every regular charge", Modifier.weight(1f))
            Switch(draft.remindCharges, { draft = draft.copy(remindCharges = it) })
          }
        }
      }

      TextButton(onClick = { showMore = !showMore }) { Text(if (showMore) "Fewer details" else "More details") }
      if (showMore) {
        Dropdown(draft.category, CATEGORIES, Modifier.fillMaxWidth(), "Category") { draft = draft.copy(category = it) }
        Dropdown(draft.status, STATUSES, Modifier.fillMaxWidth(), "Status") {
          draft = draft.copy(status = it, cancelledOn = if (it == "cancelled") draft.cancelledOn ?: today else null)
        }
        OutlinedTextField(draft.paymentMethod ?: "", { draft = draft.copy(paymentMethod = it.ifBlank { null }) }, Modifier.fillMaxWidth(), label = { Text("Paid with") }, singleLine = true)
        OutlinedTextField(draft.url ?: "", { draft = draft.copy(url = it.ifBlank { null }) }, Modifier.fillMaxWidth(), label = { Text("Manage or cancel link") }, singleLine = true)
        OutlinedTextField(draft.notes ?: "", { draft = draft.copy(notes = it.ifBlank { null }) }, Modifier.fillMaxWidth(), label = { Text("Notes") })
      }

      // Live preview from the same core the reminders use.
      val preview = draft.copy(amount = amountText.toDoubleOrNull() ?: 0.0, cycleEvery = everyText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        renewalCost = renewalText.toDoubleOrNull(), name = draft.name.ifBlank { "This" })
      val next = Schedule.nextCharge(preview, today)
      Card {
        Column(Modifier.padding(12.dp)) {
          Text("Preview", fontWeight = FontWeight.SemiBold)
          Text(if (next != null) "Next charge ${shortDate(next)} (${relDays(Dates.daysBetween(today, next))})" else "No upcoming charge")
          Text("About ${money(Schedule.round2(Schedule.monthlyEquivalent(preview)), preview.currency)} a month")
          preview.endDate?.takeIf { preview.kind == "contract" }?.let {
            Text("Contract ends ${shortDate(it)} (${relDays(Dates.daysBetween(today, it))})")
          }
        }
      }
      if (existing != null) {
        OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
          Text("Delete")
        }
      }
      Spacer(Modifier.height(40.dp))
    }
  }
  if (confirmDelete && existing != null) AlertDialog(
    onDismissRequest = { confirmDelete = false },
    title = { Text("Delete ${existing.name}?") },
    text = { Text("You can restore it from Settings, Recently deleted.") },
    confirmButton = { TextButton(onClick = { onDelete(existing.id) }) { Text("Delete") } },
    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
  )
}

fun fmtNum(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(value: String, options: List<String>, modifier: Modifier, label: String? = null, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(open, { open = it }, modifier) {
    OutlinedTextField(
      value.replaceFirstChar { it.uppercase() }, {}, readOnly = true, singleLine = true,
      label = label?.let { { Text(it) } },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
      modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(open, { open = false }) {
      options.forEach { o -> DropdownMenuItem({ Text(o.replaceFirstChar { it.uppercase() }) }, { onPick(o); open = false }) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  OutlinedTextField(
    shortDate(value), {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
    trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Outlined.CalendarMonth, "Pick date") } },
  )
  if (open) {
    val st = rememberDatePickerState(initialSelectedDateMillis = Dates.toEpochDay(value) * 86_400_000L)
    DatePickerDialog(
      onDismissRequest = { open = false },
      confirmButton = {
        TextButton(onClick = {
          st.selectedDateMillis?.let { onPick(Dates.fromEpochDay(Math.floorDiv(it, 86_400_000L))) }
          open = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    ) { DatePicker(st) }
  }
}

/** Edit several at once: shared fields apply to all, prices stay per item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEditor(subs: List<Subscription>, currency: String, onSave: (List<Subscription>) -> Unit, onClose: () -> Unit) {
  var category by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf<String?>(null) }
  var paidWith by remember { mutableStateOf("") }
  var remind by remember { mutableStateOf<List<Int>?>(null) }
  val prices = remember { mutableStateMapOf<String, String>().apply { subs.forEach { put(it.id, fmtNum(it.amount)) } } }
  BackHandler(onBack = onClose)
  Scaffold(topBar = {
    TopAppBar(
      title = { Text("Edit ${subs.size} together") },
      navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
      actions = {
        TextButton(onClick = {
          val today = Dates.today()
          onSave(subs.map { s ->
            s.copy(
              category = category ?: s.category,
              status = status ?: s.status,
              cancelledOn = when (status) { null -> s.cancelledOn; "cancelled" -> s.cancelledOn ?: today; else -> null },
              paymentMethod = paidWith.ifBlank { s.paymentMethod },
              remindDays = remind ?: s.remindDays,
              amount = prices[s.id]?.toDoubleOrNull() ?: s.amount,
            )
          })
        }) { Text("Save") }
      },
    )
  }) { pad ->
    Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Leave a field alone to keep each item's own value.", style = MaterialTheme.typography.bodySmall)
      Dropdown(category ?: "(keep)", listOf("(keep)") + CATEGORIES, Modifier.fillMaxWidth(), "Category") { category = it.takeIf { c -> c != "(keep)" } }
      Dropdown(status ?: "(keep)", listOf("(keep)") + STATUSES, Modifier.fillMaxWidth(), "Status") { status = it.takeIf { c -> c != "(keep)" } }
      OutlinedTextField(paidWith, { paidWith = it }, Modifier.fillMaxWidth(), label = { Text("Paid with") }, singleLine = true)
      Text("Remind me", fontWeight = FontWeight.SemiBold)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(remind == null, { remind = null }, label = { Text("Keep") })
        LEADS.forEach { d ->
          val cur = remind ?: emptyList()
          FilterChip(remind != null && d in cur, {
            remind = (if (d in cur) cur - d else cur + d).distinct().sorted()
          }, label = { Text(if (d == 0) "On the day" else "$d d") })
        }
      }
      Text("Prices", fontWeight = FontWeight.SemiBold)
      subs.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Tile(s.name, 32)
          Text(s.name, Modifier.weight(1f))
          OutlinedTextField(prices[s.id] ?: "", { prices[s.id] = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.width(140.dp),
            prefix = { Text(s.currency + " ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
      }
    }
  }
}
