package app.greenforrest.recur.ui

import app.greenforrest.recur.core.Dates
import java.text.NumberFormat
import java.util.Locale

fun money(amount: Double, currency: String): String {
  val nf = NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
  return "$currency ${nf.format(amount)}"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun shortDate(s: String): String {
  val c = Dates.parse(s)
  return "${c.d} ${MONTHS[c.m - 1]} ${c.y}"
}

fun monthTitle(y: Int, m: Int) = "${MONTHS[m - 1]} $y"

fun relDays(n: Long): String = when {
  n == 0L -> "today"
  n == 1L -> "tomorrow"
  n < 0 -> "${-n} days ago"
  else -> "in $n days"
}

fun cycleLabel(unit: String, every: Int): String {
  val base = when (unit) { "day" -> "day"; "week" -> "week"; "year" -> "year"; else -> "month" }
  return if (every == 1) "every $base" else "every $every ${base}s"
}

fun monogram(name: String): String {
  val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  return when {
    words.isEmpty() -> "?"
    words.size == 1 -> words[0].take(2).uppercase()
    else -> (words[0].take(1) + words[1].take(1)).uppercase()
  }
}
