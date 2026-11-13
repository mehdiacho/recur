// Charges, monthly spend and reminders. Port of web/src/core/schedule.ts;
// fixtures/parity.json keeps the two equal.
package app.greenforrest.recur.core

import kotlin.math.floor
import kotlin.math.roundToLong

object Schedule {
  fun nthCharge(s: Subscription, n: Long): String {
    val k = n * s.cycleEvery
    return when (s.cycleUnit) {
      "day" -> Dates.addDays(s.anchorDate, k)
      "week" -> Dates.addDays(s.anchorDate, 7 * k)
      "year" -> Dates.addMonths(s.anchorDate, 12 * k)
      else -> Dates.addMonths(s.anchorDate, k)
    }
  }

  private fun approxCycleDays(s: Subscription): Double = s.cycleEvery * when (s.cycleUnit) {
    "day" -> 1.0; "week" -> 7.0; "year" -> 365.2425; else -> 30.436875
  }

  fun chargeAllowed(s: Subscription, date: String): Boolean {
    if (s.deletedAt != null || s.status == "paused") return false
    if (s.cancelledOn != null && date >= s.cancelledOn) return false
    if (s.status == "cancelled" && s.cancelledOn == null) return false
    if (s.endDate != null && !s.autoRenews && date >= s.endDate) return false
    return true
  }

  fun chargesBetween(s: Subscription, from: String, to: String): List<String> {
    if (to < from || to < s.anchorDate) return emptyList()
    var n = maxOf(0L, floor(Dates.daysBetween(s.anchorDate, from) / approxCycleDays(s)).toLong() - 2)
    while (n > 0 && nthCharge(s, n) >= from) n--
    val out = mutableListOf<String>()
    var guard = 0
    while (guard < 2000) {
      val d = nthCharge(s, n)
      if (d > to) break
      if (d >= from && chargeAllowed(s, d)) out += d
      guard++; n++
    }
    return out
  }

  fun nextCharge(s: Subscription, from: String): String? =
    chargesBetween(s, from, Dates.addDays(from, 3653)).firstOrNull()

  fun isLive(s: Subscription) = s.deletedAt == null && s.status == "active"

  fun monthlyEquivalent(s: Subscription): Double {
    if (!isLive(s)) return 0.0
    val perMonth = when (s.cycleUnit) {
      "day" -> 365.2425 / 12; "week" -> 365.2425 / 7 / 12; "year" -> 1.0 / 12; else -> 1.0
    }
    return s.amount * perMonth / s.cycleEvery
  }

  data class MoneyEvent(val subId: String, val name: String, val type: String, val date: String, val amount: Double)

  fun eventsBetween(subs: List<Subscription>, from: String, to: String): List<MoneyEvent> {
    val out = mutableListOf<MoneyEvent>()
    for (s in subs) {
      if (s.deletedAt != null) continue
      for (d in chargesBetween(s, from, to)) {
        val type = if (s.kind == "trial" && d == s.anchorDate) "trial-end" else "charge"
        out += MoneyEvent(s.id, s.name, type, d, s.amount)
      }
      val end = s.endDate
      if (end != null && end >= from && end <= to && s.status != "paused" &&
        !(s.cancelledOn != null && end >= s.cancelledOn) && s.status != "cancelled"
      ) out += MoneyEvent(s.id, s.name, "contract-end", end, s.renewalCost ?: 0.0)
    }
    return out.sortedWith(compareBy<MoneyEvent> { it.date }.thenBy { it.name })
  }

  data class MonthSpend(val total: Double, val average: Double, val events: List<MoneyEvent>)

  fun monthSpend(subs: List<Subscription>, y: Int, m: Int): MonthSpend {
    val events = eventsBetween(subs, Dates.monthStart(y, m), Dates.monthEnd(y, m))
    return MonthSpend(round2(events.sumOf { it.amount }), round2(subs.sumOf { monthlyEquivalent(it) }), events)
  }

  data class Reminder(
    val key: String, val subId: String, val name: String, val type: String,
    val date: String, val daysUntil: Long, val amount: Double,
  )

  /** Reminders due today; a reminder belongs to the smallest lead >= days left. */
  fun dueReminders(subs: List<Subscription>, today: String): List<Reminder> {
    val out = mutableListOf<Reminder>()
    for (s in subs) {
      if (!isLive(s)) continue
      val leads = (listOf(0) + s.remindDays).distinct().sorted()
      val window = eventsBetween(listOf(s), today, Dates.addDays(today, leads.last().toLong()))
      for (e in window) {
        if (e.type == "charge" && !s.remindCharges) continue
        val daysUntil = Dates.daysBetween(today, e.date)
        val bucket = leads.firstOrNull { it >= daysUntil } ?: continue
        out += Reminder("${s.id}|${e.type}|${e.date}|$bucket", s.id, s.name, e.type, e.date, daysUntil, e.amount)
      }
    }
    return out.sortedBy { it.daysUntil }
  }

  fun pruneNotified(keys: List<String>, today: String) = keys.filter { (it.split('|').getOrNull(2) ?: "") >= today }

  fun round2(n: Double): Double = (n * 100).roundToLong() / 100.0

  fun daysToEnd(s: Subscription, today: String): Long? = s.endDate?.let { Dates.daysBetween(today, it) }
}
