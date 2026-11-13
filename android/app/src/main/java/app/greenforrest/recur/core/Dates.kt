// Civil dates as "YYYY-MM-DD" strings. Port of web/src/core/dates.ts, line for line.
// No time zones: a renewal is a day on the calendar, not an instant.
package app.greenforrest.recur.core

import java.util.Calendar

data class Civil(val y: Int, val m: Int, val d: Int)

object Dates {
  private val RE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

  fun parse(s: String): Civil {
    val m = RE.matchEntire(s) ?: throw IllegalArgumentException("Bad date: $s")
    return Civil(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
  }

  fun format(c: Civil): String = "%04d-%02d-%02d".format(c.y, c.m, c.d)

  fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

  fun daysInMonth(y: Int, m: Int) =
    intArrayOf(31, if (isLeap(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1]

  /** Days since 1970-01-01 (Howard Hinnant's days_from_civil). */
  fun toEpochDay(s: String): Long {
    val c = parse(s)
    val y = (c.y - if (c.m <= 2) 1 else 0).toLong()
    val era = Math.floorDiv(y, 400L)
    val yoe = y - era * 400
    val doy = (153 * (c.m + if (c.m > 2) -3 else 9) + 2) / 5 + c.d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  fun fromEpochDay(day: Long): String {
    val z = day + 719468
    val era = Math.floorDiv(z, 146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (mp + if (mp < 10) 3 else -9).toInt()
    return format(Civil((yoe + era * 400 + if (m <= 2) 1 else 0).toInt(), m, d))
  }

  fun addDays(s: String, n: Long): String = fromEpochDay(toEpochDay(s) + n)

  fun daysBetween(a: String, b: String): Long = toEpochDay(b) - toEpochDay(a)

  /** Keeps the anchor day, clamped to the month's length: Jan 31 + 1 month = Feb 28. */
  fun addMonths(anchor: String, months: Long): String {
    val c = parse(anchor)
    val total = c.y.toLong() * 12 + (c.m - 1) + months
    val ny = Math.floorDiv(total, 12L).toInt()
    val nm = (total - ny.toLong() * 12 + 1).toInt()
    return format(Civil(ny, nm, minOf(c.d, daysInMonth(ny, nm))))
  }

  fun today(): String {
    val t = Calendar.getInstance()
    return format(Civil(t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH)))
  }

  fun monthStart(y: Int, m: Int) = format(Civil(y, m, 1))
  fun monthEnd(y: Int, m: Int) = format(Civil(y, m, daysInMonth(y, m)))
}
