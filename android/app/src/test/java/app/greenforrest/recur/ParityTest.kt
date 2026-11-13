package app.greenforrest.recur

import app.greenforrest.recur.core.Dates
import app.greenforrest.recur.core.Schedule
import app.greenforrest.recur.core.Subscription
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Runs fixtures/parity.json, the same file the web core's vitest suite runs. */
class ParityTest {
  private val fx: JSONObject = JSONObject(
    listOf("../fixtures/parity.json", "../../fixtures/parity.json").map(::File).first { it.exists() }.readText(),
  )
  private val subs: Map<String, Subscription> = fx.getJSONArray("subscriptions").let { a ->
    (0 until a.length()).map { Subscription.fromJson(a.getJSONObject(it), "BWP") }.associateBy { it.id }
  }
  private fun strings(o: JSONObject, k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }

  @Test fun dateRoundTripAndClamping() {
    for (d in listOf("1970-01-01", "2000-02-29", "2024-12-31", "1969-12-31", "2100-03-01")) {
      assertEquals(d, Dates.fromEpochDay(Dates.toEpochDay(d)))
    }
    assertEquals(0L, Dates.toEpochDay("1970-01-01"))
    assertEquals("2026-02-28", Dates.addMonths("2026-01-31", 1))
    assertEquals("2026-03-31", Dates.addMonths("2026-01-31", 2))
    assertEquals("2028-02-29", Dates.addMonths("2024-02-29", 48))
    assertEquals("2025-12-31", Dates.addMonths("2026-01-31", -1))
  }

  @Test fun chargesBetween() {
    val a = fx.getJSONArray("chargesBetween")
    for (i in 0 until a.length()) {
      val c = a.getJSONObject(i)
      assertEquals(c.toString(), strings(c, "expect"), Schedule.chargesBetween(subs.getValue(c.getString("id")), c.getString("from"), c.getString("to")))
    }
  }

  @Test fun nextCharge() {
    val a = fx.getJSONArray("nextCharge")
    for (i in 0 until a.length()) {
      val c = a.getJSONObject(i)
      val expect = if (c.isNull("expect")) null else c.getString("expect")
      assertEquals(c.toString(), expect, Schedule.nextCharge(subs.getValue(c.getString("id")), c.getString("from")))
    }
  }

  @Test fun monthlyEquivalent() {
    val a = fx.getJSONArray("monthlyEquivalent")
    for (i in 0 until a.length()) {
      val c = a.getJSONObject(i)
      assertEquals(c.toString(), c.getDouble("expect"), Schedule.monthlyEquivalent(subs.getValue(c.getString("id"))), 1e-6)
    }
  }

  @Test fun monthSpend() {
    val a = fx.getJSONArray("monthSpend")
    for (i in 0 until a.length()) {
      val c = a.getJSONObject(i)
      val r = Schedule.monthSpend(subs.values.toList(), c.getInt("year"), c.getInt("month"))
      assertEquals(c.toString(), c.getDouble("total"), r.total, 1e-9)
      assertEquals(c.toString(), c.getDouble("average"), r.average, 1e-9)
    }
  }

  @Test fun dueReminders() {
    val a = fx.getJSONArray("dueReminders")
    for (i in 0 until a.length()) {
      val c = a.getJSONObject(i)
      val keys = Schedule.dueReminders(subs.values.toList(), c.getString("today")).map { it.key }
      assertEquals(c.toString(), strings(c, "expectKeys").sorted(), keys.sorted())
    }
  }

  @Test fun jsonRoundTrip() {
    for (s in subs.values) assertEquals(s, Subscription.fromJson(s.toJson(), "BWP"))
  }
}
