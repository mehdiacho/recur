package app.greenforrest.recur.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.greenforrest.recur.core.Dates
import app.greenforrest.recur.core.Schedule
import app.greenforrest.recur.core.Store
import app.greenforrest.recur.core.Subscription

@Composable
fun Tile(name: String, size: Int = 40) {
  Box(
    Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(colorFor(name)),
    contentAlignment = Alignment.Center,
  ) { Text(monogram(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.38).sp) }
}

@Composable
private fun SectionTitle(t: String) =
  Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
fun OverviewScreen(store: Store, modifier: Modifier, onOpen: (Subscription) -> Unit) {
  val today = remember { Dates.today() }
  val t = Dates.parse(today)
  var ym by remember { mutableStateOf(t.y to t.m) }
  val (y, m) = ym
  val subs = store.subscriptions.filter { it.deletedAt == null }
  val spend = remember(store, ym) { Schedule.monthSpend(subs, y, m) }
  val reminders = remember(store) { Schedule.dueReminders(subs, today) }
  val ending = remember(store) {
    subs.filter { Schedule.isLive(it) && it.endDate != null }
      .mapNotNull { s -> Schedule.daysToEnd(s, today)?.takeIf { it in 0..120 }?.let { s to it } }
      .sortedBy { it.second }
  }
  val next30 = remember(store) { Schedule.eventsBetween(subs, today, Dates.addDays(today, 30)) }
  val byCat = remember(store) {
    subs.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { Schedule.monthlyEquivalent(it) } }
      .filterValues { it > 0 }.toList().sortedByDescending { it.second }
  }
  val cur = store.currency
  val byId = subs.associateBy { it.id }

  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { ym = if (m == 1) (y - 1) to 12 else y to (m - 1) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
        }
        Text(monthTitle(y, m), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { ym = if (m == 12) (y + 1) to 1 else y to (m + 1) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
        }
      }
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Text("Going out this month", style = MaterialTheme.typography.labelLarge)
          Text(money(spend.total, cur), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(6.dp))
          Text(
            "Average ${money(spend.average, cur)} a month · ${money(Schedule.round2(spend.average * 12), cur)} a year",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    if (reminders.isNotEmpty()) {
      item { SectionTitle("Needs attention") }
      items(reminders, key = { "r" + it.key }) { r ->
        ListItem(
          headlineContent = { Text(app.greenforrest.recur.notify.Reminders.title(r)) },
          supportingContent = { Text(app.greenforrest.recur.notify.Reminders.body(r, cur)) },
          leadingContent = { Tile(r.name) },
          modifier = Modifier.clickable { byId[r.subId]?.let(onOpen) },
        )
      }
    }
    if (ending.isNotEmpty()) {
      item { SectionTitle("Contracts ending") }
      items(ending, key = { "e" + it.first.id }) { (s, days) ->
        ListItem(
          headlineContent = { Text(s.name) },
          supportingContent = {
            Text("Ends ${shortDate(s.endDate!!)}" + (s.renewalCost?.let { " · renewal ${money(it, cur)}" } ?: ""))
          },
          leadingContent = { Tile(s.name) },
          trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
              Text("$days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              Text(if (days == 1L) "day" else "days", style = MaterialTheme.typography.labelSmall)
            }
          },
          modifier = Modifier.clickable { onOpen(s) },
        )
      }
    }
    item { SectionTitle("This month") }
    if (spend.events.isEmpty()) item { Text("Nothing due in ${monthTitle(y, m)}.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(spend.events, key = { "m" + it.subId + it.type + it.date }) { e -> EventRow(e, cur) { byId[e.subId]?.let(onOpen) } }
    item { SectionTitle("Next 30 days") }
    if (next30.isEmpty()) item { Text("Nothing in the next 30 days.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(next30, key = { "n" + it.subId + it.type + it.date }) { e -> EventRow(e, cur) { byId[e.subId]?.let(onOpen) } }
    if (byCat.isNotEmpty()) {
      item { SectionTitle("By category, per month") }
      val max = byCat.first().second
      items(byCat, key = { "c" + it.first }) { (c, v) ->
        Column(Modifier.padding(vertical = 4.dp)) {
          Row { Text(c.replaceFirstChar { it.uppercase() }, Modifier.weight(1f)); Text(money(Schedule.round2(v), cur)) }
          LinearProgressIndicator(progress = { (v / max).toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
      }
    }
  }
}

@Composable
private fun EventRow(e: Schedule.MoneyEvent, cur: String, onClick: () -> Unit) {
  val label = when (e.type) { "contract-end" -> "Contract ends"; "trial-end" -> "Trial ends, first charge"; else -> "Charge" }
  ListItem(
    headlineContent = { Text(e.name) },
    supportingContent = { Text("$label · ${shortDate(e.date)}") },
    leadingContent = { Tile(e.name, 36) },
    trailingContent = { Text(if (e.amount > 0) money(e.amount, cur) else "—") },
    modifier = Modifier.clickable(onClick = onClick),
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
  store: Store, modifier: Modifier,
  onOpen: (Subscription) -> Unit, onDelete: (Set<String>) -> Unit, onBulkEdit: (Set<String>) -> Unit,
) {
  val today = remember { Dates.today() }
  var query by remember { mutableStateOf("") }
  var selecting by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf(setOf<String>()) }
  val subs = store.subscriptions.filter { it.deletedAt == null }
    .filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
    .sortedWith(compareBy<Subscription> { it.status != "active" }.thenBy { Schedule.nextCharge(it, today) ?: "9999" })

  Column(modifier.fillMaxSize()) {
    Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        query, { query = it }, Modifier.weight(1f), singleLine = true,
        placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
      )
      TextButton(onClick = { selecting = !selecting; selected = emptySet() }) { Text(if (selecting) "Done" else "Select") }
    }
    if (selecting) {
      Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${selected.size} selected", Modifier.weight(1f))
        TextButton(onClick = { selected = if (selected.size == subs.size) emptySet() else subs.map { it.id }.toSet() }) {
          Text(if (selected.size == subs.size && subs.isNotEmpty()) "None" else "All")
        }
        TextButton(enabled = selected.isNotEmpty(), onClick = { onBulkEdit(selected) }) { Text("Edit together") }
        TextButton(enabled = selected.isNotEmpty(), onClick = { onDelete(selected); selected = emptySet(); selecting = false }) {
          Text("Delete", color = if (selected.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
      }
    }
    if (subs.isEmpty()) {
      Text(
        if (query.isBlank()) "Nothing yet. Tap + to add your first subscription or contract." else "No match for \"$query\".",
        Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
      items(subs, key = { it.id }) { s ->
        val next = Schedule.nextCharge(s, today)
        val sub = buildString {
          append(money(s.amount, s.currency)); append(" "); append(cycleLabel(s.cycleUnit, s.cycleEvery))
          if (s.status != "active") append(" · ${s.status}")
          else if (next != null) append(" · next ${shortDate(next)}")
          s.endDate?.let { append(" · ends ${shortDate(it)}") }
        }
        ListItem(
          headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          supportingContent = { Text(sub) },
          leadingContent = {
            if (selecting) Checkbox(s.id in selected, { c -> selected = if (c) selected + s.id else selected - s.id })
            else Tile(s.name)
          },
          overlineContent = { Text(s.kind.replaceFirstChar { it.uppercase() } + " · " + s.category) },
          modifier = Modifier.combinedClickable(
            onClick = { if (selecting) selected = if (s.id in selected) selected - s.id else selected + s.id else onOpen(s) },
            onLongClick = { selecting = true; selected = selected + s.id },
          ),
        )
      }
    }
  }
}
