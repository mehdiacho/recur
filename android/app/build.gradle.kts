plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "app.greenforrest.recur"
  compileSdk { version = release(37) { minorApiLevel = 0 } }

  defaultConfig {
    applicationId = "app.greenforrest.recur"
    // 26 so the adaptive icon needs no bitmap fallback.
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }

  buildFeatures { compose = true }

  testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.work.runtime.ktx)

  testImplementation(libs.junit)
  // android.jar stubs org.json for local unit tests; the real library replaces it there.
  testImplementation(libs.org.json)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
