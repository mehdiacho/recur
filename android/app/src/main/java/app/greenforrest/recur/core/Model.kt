// The Recur data model. Port of web/src/core/model.ts; the JSON shape is identical so an
// export from either app imports into the other. See docs/data-model.md.
package app.greenforrest.recur.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val SCHEMA_VERSION = 1

val KINDS = listOf("subscription", "contract", "trial", "bill")
val CYCLE_UNITS = listOf("day", "week", "month", "year")
val STATUSES = listOf("active", "paused", "cancelled")
val CATEGORIES = listOf(
  "streaming", "music", "phone", "internet", "software", "cloud", "gaming",
  "news", "fitness", "insurance", "utilities", "education", "other",
)

data class Subscription(
  val id: String,
  val name: String,
  val provider: String? = null,
  val category: String = "other",
  val kind: String = "subscription",
  val amount: Double,
  val currency: String = "BWP",
  val cycleUnit: String = "month",
  val cycleEvery: Int = 1,
  val anchorDate: String,
  val endDate: String? = null,
  val autoRenews: Boolean = true,
  val renewalCost: Double? = null,
  val remindDays: List<Int> = listOf(3),
  val remindCharges: Boolean = true,
  val status: String = "active",
  val cancelledOn: String? = null,
  val paymentMethod: String? = null,
  val url: String? = null,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null,
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("provider", provider ?: JSONObject.NULL)
    put("category", category); put("kind", kind); put("amount", amount); put("currency", currency)
    put("cycleUnit", cycleUnit); put("cycleEvery", cycleEvery); put("anchorDate", anchorDate)
    put("endDate", endDate ?: JSONObject.NULL); put("autoRenews", autoRenews)
    put("renewalCost", renewalCost ?: JSONObject.NULL)
    put("remindDays", JSONArray(remindDays)); put("remindCharges", remindCharges)
    put("status", status); put("cancelledOn", cancelledOn ?: JSONObject.NULL)
    put("paymentMethod", paymentMethod ?: JSONObject.NULL); put("url", url ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt); put("updatedAt", updatedAt); put("deletedAt", deletedAt ?: JSONObject.NULL)
  }

  companion object {
    /** Port of normaliseSubscription: any JSON in, a valid Subscription out. */
    fun fromJson(o: JSONObject, currency: String): Subscription {
      val kind = o.optString("kind").takeIf { it in KINDS } ?: "subscription"
      val d = defaultsFor(kind)
      val now = System.currentTimeMillis()
      val remind = o.optJSONArray("remindDays")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optDouble(i).takeIf { !it.isNaN() }?.toInt()?.coerceAtLeast(0) }
          .distinct().sortedDescending()
      } ?: d.remindDays
      return Subscription(
        id = o.str("id") ?: newId(),
        name = o.str("name")?.trim()?.ifEmpty { null } ?: "Untitled",
        provider = o.str("provider"),
        category = o.optString("category").takeIf { it in CATEGORIES } ?: "other",
        kind = kind,
        amount = o.optDouble("amount").takeIf { it.isFinite() } ?: 0.0,
        currency = o.str("currency") ?: currency,
        cycleUnit = o.optString("cycleUnit").takeIf { it in CYCLE_UNITS } ?: "month",
        cycleEvery = o.optDouble("cycleEvery", 1.0).let { if (it.isFinite()) it.toInt() else 1 }.coerceAtLeast(1),
        anchorDate = o.str("anchorDate") ?: Dates.today(),
        endDate = o.str("endDate"),
        autoRenews = if (o.has("autoRenews") && !o.isNull("autoRenews")) o.optBoolean("autoRenews") else d.autoRenews,
        renewalCost = if (o.isNull("renewalCost") || o.optString("renewalCost") == "") null else o.optDouble("renewalCost").takeIf { it.isFinite() },
        remindDays = remind,
        remindCharges = if (o.has("remindCharges") && !o.isNull("remindCharges")) o.optBoolean("remindCharges") else d.remindCharges,
        status = o.optString("status").takeIf { it in STATUSES } ?: "active",
        cancelledOn = o.str("cancelledOn"),
        paymentMethod = o.str("paymentMethod"),
        url = o.str("url"),
        notes = o.str("notes"),
        createdAt = o.long("createdAt") ?: now,
        updatedAt = o.long("updatedAt") ?: now,
        deletedAt = o.long("deletedAt"),
      )
    }
  }
}

data class Defaults(val remindDays: List<Int>, val remindCharges: Boolean, val autoRenews: Boolean)

fun defaultsFor(kind: String): Defaults = when (kind) {
  "contract" -> Defaults(listOf(60, 30, 7, 1), false, true)
  "trial" -> Defaults(listOf(3, 1), false, true)
  else -> Defaults(listOf(3), true, true)
}

data class Store(
  val schema: Int = SCHEMA_VERSION,
  val currency: String = "BWP",
  val subscriptions: List<Subscription> = emptyList(),
  val notified: List<String> = emptyList(),
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("schema", schema); put("currency", currency)
    put("subscriptions", JSONArray(subscriptions.map { it.toJson() }))
    put("notified", JSONArray(notified))
  }

  companion object {
    fun fromJson(o: JSONObject): Store {
      val currency = o.str("currency") ?: "BWP"
      val subs = o.optJSONArray("subscriptions")
      val notified = o.optJSONArray("notified")
      return Store(
        SCHEMA_VERSION, currency,
        subs?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Subscription.fromJson(it, currency) } } ?: emptyList(),
        notified?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } } ?: emptyList(),
      )
    }
  }
}

fun newId(): String = UUID.randomUUID().toString()

private fun JSONObject.str(k: String): String? =
  if (!has(k) || isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

private fun JSONObject.long(k: String): Long? =
  if (!has(k) || isNull(k)) null else optDouble(k).takeIf { it.isFinite() }?.toLong()
