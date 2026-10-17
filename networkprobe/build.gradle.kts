plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "org.localroute.networkprobe"
    compileSdk = 35
    defaultConfig { applicationId = "org.localroute.networkprobe"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "test-only" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
