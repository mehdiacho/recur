package app.greenforrest.recur.ui

import androidx.compose.ui.graphics.Color

// Mirrors web/src/core/presets.ts. No prices on purpose.
data class Preset(val name: String, val category: String, val kind: String = "subscription", val cycleUnit: String = "month", val color: Long, val url: String? = null)

val PRESETS = listOf(
  Preset("Netflix", "streaming", color = 0xFFE50914, url = "https://www.netflix.com/account"),
  Preset("Showmax", "streaming", color = 0xFF1A1AFF),
  Preset("DStv", "streaming", color = 0xFF0072CE),
  Preset("Disney+", "streaming", color = 0xFF113CCF),
  Preset("Prime Video", "streaming", color = 0xFF00A8E1),
  Preset("YouTube Premium", "streaming", color = 0xFFFF0033),
  Preset("Spotify", "music", color = 0xFF1DB954),
  Preset("Apple Music", "music", color = 0xFFFA2D48),
  Preset("Mascom", "phone", color = 0xFFE30613),
  Preset("Orange", "phone", color = 0xFFFF7900),
  Preset("BTC / beMobile", "phone", color = 0xFF00A0DF),
  Preset("Home internet", "internet", color = 0xFF5B6CFF),
  Preset("ChatGPT Plus", "software", color = 0xFF10A37F),
  Preset("Claude", "software", color = 0xFFD97757),
  Preset("Microsoft 365", "software", cycleUnit = "year", color = 0xFFD83B01),
  Preset("Adobe", "software", color = 0xFFFA0F00),
  Preset("GitHub", "software", color = 0xFF6E5494),
  Preset("iCloud+", "cloud", color = 0xFF3693F3),
  Preset("Google One", "cloud", color = 0xFF4285F4),
  Preset("Xbox Game Pass", "gaming", color = 0xFF107C10),
  Preset("PlayStation Plus", "gaming", color = 0xFF003791),
  Preset("Gym", "fitness", color = 0xFFFF4F81),
  Preset("Insurance", "insurance", kind = "bill", color = 0xFF7A5AF8),
  Preset("Domain name", "software", cycleUnit = "year", color = 0xFF0EA5A4),
)

/** Same rule as the web app: preset colour, else a hue from the name at 55% / 45%. */
fun colorFor(name: String): Color {
  PRESETS.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return Color(it.color) }
  var h = 0
  for (ch in name) h = (h * 31 + ch.code) % 360
  return Color.hsl(h.toFloat(), 0.55f, 0.45f)
}
