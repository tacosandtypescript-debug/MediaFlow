import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9.x provides Kotlin Android support; applying the standalone
    // Kotlin Android plugin would register the Kotlin extension twice.
    id("com.android.library")
}

android {
    namespace = "com.mediaflow.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":domain"))
    implementation(project(":core:model"))

    // One coherent Media3 release for offline downloading.
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-datasource:1.9.0")
    implementation("androidx.media3:media3-database:1.9.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.media:media:1.7.0")
    // yt-dlp is isolated in :data so platform extraction never leaks into UI.
    implementation("dev.ffmpegkit-maintained:yt-dlp-android:2.0.2")
    // Native libmpv multimedia engine
    api("io.github.abdallahmehiz:mpv-android-lib:0.1.12")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
