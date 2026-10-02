import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)

    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Last released versionCode was 4, so never go below 5.
val appVersionCode: Int =
    run {
        val explicit = (findProperty("versionCode") as String?) ?: System.getenv("VERSION_CODE")
        explicit?.toIntOrNull()
            ?: runCatching {
                val p =
                    ProcessBuilder("git", "rev-list", "--count", "HEAD")
                        .directory(rootDir)
                        .redirectErrorStream(true)
                        .start()
                val out =
                    p.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                p.waitFor()
                out.toInt()
            }.getOrNull()?.let { maxOf(it, 5) }
            ?: 5
    }

android {
    namespace = "zuanvfx01.aw22xxx_leds"
    compileSdk = 37

    defaultConfig {
        applicationId = "zuanvfx01.aw22xxx_leds"
        // 29 so Android 10/11 can still install and get the plain stock UI; the Liquid Glass UI is
        // gated at runtime (Android 12+ = blur, Android 13+ = full refraction), see ui/glass/GlassSupport.kt.
        minSdk = 29
        targetSdk = 37
        // versionCode must only ever go UP, or Android refuses to update the installed app.
        // Priority: -PversionCode / VERSION_CODE env  ->  git commit count  ->  floor.
        versionCode = appVersionCode
        versionName = (findProperty("versionName") as String?)
            ?: System.getenv("VERSION_NAME")
            ?: "1.1.0"
    }

    buildTypes {
        release {
            // R8 optimization breaks protobuf-lite generated Settings fields
            // (e.g. completedIntro_) on this build. Keep release unminified
            // until the protobuf/R8 combination is updated.
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            proto {
                srcDir("src/main/proto")
            }

            assets.srcDir("build/generated/assets")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)

        // Material 3 Expressive: a few APIs are still gated behind opt-in annotations on the
        // 1.5.0-alpha line. Opting in module-wide keeps the UI code free of @OptIn noise.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// Fix for: "[Hilt] Provided Metadata instance has version 2.4.0, while maximum supported
// version is 2.3.0". backdrop pulls kotlin-stdlib 2.4.x, so Hilt must read 2.4 metadata.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
            useVersion(libs.versions.kotlinMetadata.get())
        }
    }
}

dependencies {
    // settings
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)

    // yay
    implementation(libs.compose.colorpicker)

    // Liquid Glass UI (Android 12+ only at runtime; the library itself no-ops below API 31)
    implementation(libs.kyant.backdrop)
    // Capsule / RoundedRectangle used by the Liquid Glass components in ui/glass (backdrop only exposes it at runtime)
    implementation(libs.kyant.shapes)

    // hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // default
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    // android.jar only has stubs for org.json on a plain JVM, so tests need the real one.
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}

protobuf {
    protoc {
        // On-device build (ACS) uses the local protoc; everywhere else (CI, desktop) uses Maven.
        val localProtoc = file("/data/data/com.acside/files/usr/bin/protoc")
        if (localProtoc.exists()) {
            path = localProtoc.absolutePath
        } else {
            artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

// ACS_SIGNING_CONFIG_START
// ONE keystore for debug AND release, so an APK from either build can update the other
// (a different key makes Android reject the update or wipe the app data).
// Source: app/keystore.properties (storeFile/storePassword/keyAlias/keyPassword)
// or the env vars KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD (CI).
val keystorePropertiesFile = file("keystore.properties")
val acsProps =
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile
            .readLines()
            .filter { '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    } else {
        emptyMap()
    }

fun signingValue(
    prop: String,
    env: String,
): String? = acsProps[prop]?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val signStoreFile = signingValue("storeFile", "KEYSTORE_FILE")?.let { file(it) }
val hasReleaseKey = signStoreFile != null && signStoreFile.exists()

android {
    signingConfigs {
        if (hasReleaseKey) {
            maybeCreate("release").apply {
                storeFile = signStoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            // No key => deliberately UNSIGNED (clearly not installable), never silently debug-signed.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        }
        getByName("debug") {
            // Same key as release when available; otherwise AGP's default debug key.
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
}

if (!hasReleaseKey) {
    logger.warn("WARNING: no release keystore configured - release APK will be UNSIGNED and debug uses the default debug key.")
}
// ACS_SIGNING_CONFIG_END
