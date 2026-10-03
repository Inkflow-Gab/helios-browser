plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.helios.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.helios.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig is off by default from AGP 8; the About sheet reports the real version name.
        buildConfig = true
    }

    // Deliberately no `ndk { abiFilters }` and no `externalNativeBuild`.
    //
    // libhelios_adblock.so is cross-compiled by `cargo ndk` in the `adblock-engine` CI job and
    // dropped into app/src/jniLibs/<abi>/, which AGP packages automatically. That keeps the Rust
    // toolchain out of Gradle entirely: `./gradlew assembleDebug` needs no NDK, no CMake and no
    // cargo, and just packages whatever libraries are present. Building the library is a separate,
    // explicit step rather than something Gradle triggers implicitly.
    //
    // When the directory is absent — a local build, or someone who skips the Rust step — the app
    // still compiles and ships. `NativeAdBlock.isAvailable` is then false and blocking falls back
    // to the small built-in host list. That is a real limitation rather than a crash, and the
    // shields sheet says so in words instead of claiming protection it does not have.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Assets are left to aapt's default compression. The bundled lists are already gzipped, so
    // deflating them again buys very little, and letting it happen keeps this file free of a
    // `noCompress` exception that would have to be remembered if the format ever changes.
}

ksp {
    // Room writes the generated schema JSON here; commit it so migrations can be diffed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // No material3-window-size-class: the adaptive layout reads LocalConfiguration, which already
    // reports window bounds. See ui/adaptive/HeliosLayoutCompose.kt for why.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // AndroidX WebKit: hardware-accelerated Chromium WebView, Safe Browsing
    implementation("androidx.webkit:webkit:1.11.0")

    // GeckoView: Firefox's engine, published by Mozilla and not by Google.
    //
    // Why it is here and not an extension host we wrote: `android.webkit.WebView` has no extension
    // API at all, so no amount of code in this app can load one. Gecko is Firefox, so the whole
    // WebExtensions system comes with it -- uBlock Origin, Bitwarden, Dark Reader, containers.
    //
    // What it costs, stated plainly rather than discovered later:
    //   - The AAR is ~231 MB because it ships every ABI in one file. Split per-ABI it lands nearer
    //     100-140 MB, against roughly 11 MB today.
    //   - The engine/ layer is replaced. Nothing in ui/, domain/, data/ or di/ changes.
    //   - adblock-rust retires. Gecko ships tracking protection and uBlock works, which is strictly
    //     better than the list of hosts this app compiled in.
    //
    // Declared but not yet consumed: engine/GeckoSessionHost.kt is the first slice. Nothing calls
    // into Gecko until that exists, so if this dependency turns out to be unworkable the deletion is
    // one line rather than an unwind.
    implementation("org.mozilla.geckoview:geckoview:157.0.20260924084938")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room: bookmarks + history
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore: app settings + session snapshot
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}