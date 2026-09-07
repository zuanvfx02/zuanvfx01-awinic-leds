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

android {
    namespace = "zuanvfx01.aw22xxx_leds"
    compileSdk = 37

    defaultConfig {
        applicationId = "zuanvfx01.aw22xxx_leds"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

dependencies {
    // settings
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)

    // yay
    implementation(libs.compose.colorpicker)

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
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.compose.material:material-icons-core:1.7.8") 
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}

protobuf {
    protoc {
        path = "/data/data/com.acside/files/usr/bin/protoc"
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
