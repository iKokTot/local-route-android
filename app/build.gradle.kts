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
        minSdk = 26; targetSdk = 35; versionCode = 41; versionName = "0.24.17"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/tgwsJniLibs"))
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/openfluxJniLibs"))
    sourceSets.getByName("androidTest").jniLibs.srcDir(layout.buildDirectory.dir("generated/openfluxTestJniLibs"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }

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
    implementation(files("libs/libbox-1.14.4.aar"))
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

val checkOpenFlux = tasks.register("checkOpenFlux") {
    doLast {
        val revision=layout.buildDirectory.file("generated/openfluxJniLibs/revision.txt").get().asFile
        check(revision.isFile && revision.readText().trim()=="f492a1a33b7581ad8f88118f97e0b866adda3cdc") {
            "Rebuild pinned OpenFlux 0.17.1 helpers; see docs/OPENFLUX.md"
        }
        listOf("arm64-v8a","armeabi-v7a","x86_64").forEach { abi ->
            check(layout.buildDirectory.file("generated/openfluxJniLibs/$abi/libopenflux.so").get().asFile.isFile) {
                "Build pinned OpenFlux helpers first; see docs/OPENFLUX.md ($abi missing)"
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(checkOpenFlux) }

tasks.register<Exec>("buildHevSocks5Tunnel") {
    group = "build"
    dependsOn(prepareHevSources)
    val ndk = android.ndkDirectory
    executable = if (System.getProperty("os.name").startsWith("Windows", true)) "$ndk\\ndk-build.cmd" else "$ndk/ndk-build"
    args("-j4", "APP_MODULES=hev-socks5-tunnel", "NDK_PROJECT_PATH=build/intermediates/ndkBuild", "NDK_LIBS_OUT=src/main/jniLibs", "APP_BUILD_SCRIPT=src/main/jni/Android.mk", "NDK_APPLICATION_MK=src/main/jni/Application.mk")
}
tasks.named("preBuild").configure { dependsOn("buildHevSocks5Tunnel") }

val prepareLibbox = tasks.register("prepareLibbox") {
    val artifact = file("libs/libbox-1.14.4.aar")
    val url = "https://github.com/singbox-android/libbox/releases/download/1.14.4/libbox.aar"
    val expectedSha256 = "d2685924a657705beda44e94c6dc499e781df1ad20d65474e3591ec2f8b96465"
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
    inputs.property("version", "2.7.0")
    val archive = layout.buildDirectory.file("downloads/tgws-2.7.0.apk")
    val destination = layout.buildDirectory.dir("generated/tgwsJniLibs")
    outputs.dir(destination)
    doLast {
        val apk = archive.get().asFile
        apk.parentFile.mkdirs()
        if (!apk.isFile) URL("https://github.com/valnesfjord/tg-ws-proxy-rs/releases/download/v2.7.0/tg-ws-proxy-rs-android-2.7.0-universal.apk").openStream().use { input -> apk.outputStream().use(input::copyTo) }
        val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        check(digest == "c88cc54442958803eb8a99abc7d2c2052dcd33ca61adf6d2a86d7e28eed6d0ae") { "TGWs artifact checksum mismatch" }
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
