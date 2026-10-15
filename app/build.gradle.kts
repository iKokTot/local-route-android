plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android {
    namespace = "org.localroute.android"
    compileSdk = 35
    // Match the toolchain used by the proven ByeDPIAndroid 1.2 build.
    ndkVersion = "25.1.8937393"
    defaultConfig { applicationId = "org.localroute.android"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.9.0-mvp"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
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
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val prepareHevSources = tasks.register("prepareHevSources") {
    group = "build"
    doLast {
        // Git stores upstream symlinks as tiny text files on Windows systems
        // without symlink support. Materialize them before ndk-build.
        val root = file("src/main/jni/hev-socks5-tunnel")
        root.walkTopDown().filter { it.isFile && it.length() in 1..300 }.forEach { candidate ->
            val relative = runCatching { candidate.readText().trim() }.getOrNull() ?: return@forEach
            if (!relative.matches(Regex("^(?:\\.\\.?[/\\\\])+[A-Za-z0-9._/\\\\-]+$"))) return@forEach
            val target = candidate.parentFile.resolve(relative).canonicalFile
            if (target.isFile && target != candidate.canonicalFile) candidate.writeBytes(target.readBytes())
        }
        val cc = root.resolve("third-part/lwip/src/ports/unix/include/cc.h")
        if (cc.isFile) {
            val source = cc.readText()
            if ("#define LWIP_ERRNO_INCLUDE <errno.h>" in source) {
                cc.writeText(source.replace(
                    "#define LWIP_ERRNO_INCLUDE <errno.h>",
                    "/* Android NDK: include errno directly instead of a macro-expanded include. */\n#define LWIP_ERRNO_STDINCLUDE 1"
                ))
            }
        }
    }
}

tasks.register<Exec>("buildHevSocks5Tunnel") {
    group = "build"
    dependsOn(prepareHevSources)
    val ndk = android.ndkDirectory
    executable = if (System.getProperty("os.name").startsWith("Windows", true)) "$ndk\\ndk-build.cmd" else "$ndk/ndk-build"
    args("NDK_PROJECT_PATH=build/intermediates/ndkBuild", "NDK_LIBS_OUT=src/main/jniLibs", "APP_BUILD_SCRIPT=src/main/jni/Android.mk", "NDK_APPLICATION_MK=src/main/jni/Application.mk")
}
tasks.named("preBuild").configure { dependsOn("buildHevSocks5Tunnel") }
