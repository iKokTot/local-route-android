import java.util.Properties
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

val releaseSigningFile = rootProject.file(providers.gradleProperty("releaseSigningFile").orNull ?: "release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use(::load)
}

android {
    namespace = "org.localroute.android"
    compileSdk = 35
    testBuildType = providers.gradleProperty("androidTestBuildType").getOrElse("release")
    // Match the toolchain used by the proven ByeDPIAndroid 1.2 build.
    ndkVersion = "25.1.8937393"
    defaultConfig {
        applicationId = providers.gradleProperty("applicationId").getOrElse("org.localroute.community")
        manifestPlaceholders["applicationLabel"] = providers.gradleProperty("applicationLabel").getOrElse("Local Route")
        minSdk = 26; targetSdk = 35; versionCode = 36; versionName = "0.24.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/tgwsJniLibs"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") }

    signingConfigs {
        create("release") {
            val storeFileValue = releaseSigningProperties.getProperty("storeFile")
            if (storeFileValue != null) {
                storeFile = releaseSigningFile.parentFile.resolve(storeFileValue)
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
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
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Pinned GPL-3.0 Android binding of sing-box for VLESS. The AAR is fetched
    // by prepareLibbox and verified before Gradle packages it.
    implementation(files("libs/libbox.aar"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
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

val prepareLibbox = tasks.register("prepareLibbox") {
    val artifact = file("libs/libbox.aar")
    val url = "https://github.com/singbox-android/libbox/releases/download/1.14.0/libbox.aar"
    val expectedSha256 = "13fbeabcb4622250762afa1b137daaba861a4dc1875643d18621339912f32330"
    outputs.file(artifact)
    doLast {
        artifact.parentFile.mkdirs()
        if (!artifact.isFile) URL(url).openStream().use { input -> artifact.outputStream().use(input::copyTo) }
        val digest = MessageDigest.getInstance("SHA-256").digest(artifact.readBytes())
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        check(digest == expectedSha256) { "libbox.aar SHA-256 does not match the pinned release" }
    }
}
tasks.named("preBuild").configure { dependsOn(prepareLibbox) }

val prepareTgws = tasks.register("prepareTgws") {
    val archive = layout.buildDirectory.file("downloads/tgws-2.5.4.apk")
    val destination = layout.buildDirectory.dir("generated/tgwsJniLibs")
    outputs.dir(destination)
    doLast {
        val apk = archive.get().asFile
        apk.parentFile.mkdirs()
        if (!apk.isFile) URL("https://github.com/valnesfjord/tg-ws-proxy-rs/releases/download/v2.5.4/tg-ws-proxy-rs-android-2.5.4-universal.apk").openStream().use { input -> apk.outputStream().use(input::copyTo) }
        val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        check(digest == "04e40c1cab8dca82e81749ec9829bdc5d8420a301469e84b139b9172b5524f08") { "TGWs artifact checksum mismatch" }
        ZipFile(apk).use { zip ->
            listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach { abi ->
                val entry = zip.getEntry("lib/$abi/libtg_ws_proxy_jni.so") ?: error("Missing TGWs ABI: $abi")
                val output = destination.get().file("$abi/libtg_ws_proxy_jni.so").asFile
                output.parentFile.mkdirs()
                zip.getInputStream(entry).use { input -> output.outputStream().use(input::copyTo) }
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(prepareTgws) }

// Use the reviewed UI in this repository, independent of sibling desktop folders.
val sharedUiSource = rootProject.file("shared-ui")
val prepareSharedUi = tasks.register<Sync>("prepareSharedUi") {
    from(sharedUiSource)
    into("src/main/assets/ui")
}
tasks.named("preBuild").configure { dependsOn(prepareSharedUi) }
