plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android {
    namespace = "org.localroute.android"
    compileSdk = 35
    ndkVersion = "30.0.16248370"
    defaultConfig { applicationId = "org.localroute.android"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0-mvp"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.register<Exec>("buildHevSocks5Tunnel") {
    group = "build"
    val ndk = android.ndkDirectory
    executable = if (System.getProperty("os.name").startsWith("Windows", true)) "$ndk\\ndk-build.cmd" else "$ndk/ndk-build"
    args("NDK_PROJECT_PATH=build/intermediates/ndkBuild", "NDK_LIBS_OUT=src/main/jniLibs", "APP_BUILD_SCRIPT=src/main/jni/Android.mk", "NDK_APPLICATION_MK=src/main/jni/Application.mk")
}
tasks.named("preBuild").configure { dependsOn("buildHevSocks5Tunnel") }
