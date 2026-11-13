// No `kotlin-android` alias: AGP 9 integrates the Kotlin plugin itself. Same setup as SmartSpend.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
