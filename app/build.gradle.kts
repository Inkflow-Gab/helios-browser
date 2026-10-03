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

    // One APK per ABI.
    //
    // This exists because of GeckoView. libxul.so is ~59 MB on its own for a single 32-bit ABI,
    // and the engine ships it for every ABI we build, so a universal APK carries three copies.
    // That is roughly 230 MB of native library to install on a phone, which is not acceptable.
    //
    // Splits is the correct tool: it builds once and emits one APK per ABI, so a device downloads
    // the one that matches it and nothing else. It is declared but not enabled, so `assembleDebug`
    // still produces a single universal APK that installs anywhere -- which is what makes the
    // side-loaded debug build usable and what keeps CI's three-ABI check meaningful.
    //
    // Enabling it means the release artifact stops being one file, so the upload step in the
    // workflow has to publish all of them rather than the first. That is left until the engine is
    // actually in use, at which point there is something to install worth measuring.
    splits {
        abi {
            isEnable = false
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
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
    // API at all, so no amount of code in this app can load one. Gecko *is* Firefox, so the whole
    // WebExtensions system arrives with it -- uBlock Origin, Bitwarden, Dark Reader, containers.
    // That is the entire reason for this dependency and the whole point of the migration.
    //
    // Why the omni artifact rather than the per-ABI ones:
    //   Mozilla also publishes geckoview-arm64-v8a (73 MB), -armeabi-v7a (70 MB) and -x86_64
    //   (77 MB), which look attractive. They cannot be declared together: each declares the same
    //   Gradle capability `org.mozilla.geckoview:geckoview:<version>`, so Gradle rejects all three
    //   at once as conflicting providers and the build fails to resolve. They are alternatives for
    //   a project targeting exactly one ABI, and this project targets three, because the adblock
    //   engine is cross-compiled for all three and CI fails the APK if any ABI is missing.
    //
    // How the size is actually controlled, then:
    //   The omni AAR is 262 MB on disk because it carries libxul.so for every ABI. A universal APK
    //   is therefore large, and there is no way around that while one APK serves three ABIs. The
    //   fix is the `splits` block below, which is what splits are for: one small APK per ABI, with
    //   libxul.so for just the device's own architecture. That is ~73-77 MB installed rather than
    //   ~262 MB, and it is a packaging decision rather than an engine one.
    //
    // Why version 128 and not the latest 157:
    //   157 requires compileSdk 37. This module compiles against 34 and the AGP in use tops out at
    //   34, so taking the newest engine means migrating AGP, Gradle, Kotlin, the Compose compiler
    //   plugin and KSP together -- five version-locked pieces at once. Version 128 needs nothing
    //   moved: it asks for kotlin-stdlib 1.9.24, which our Kotlin 2.0.0 already satisfies, and its
    //   transitive versions are all at or below ours (core 1.13.1 is exactly what we declare,
    //   lifecycle 2.7.0 is below our 2.8.3). So the engine lands first and the toolchain moves
    //   second, with a working browser in between rather than after both.
    //
    // Declared but not yet consumed: nothing calls into Gecko until engine/GeckoSessionHost.kt
    // exists, so if this proves unworkable the removal is one line, not an unwind.
    implementation("org.mozilla.geckoview:geckoview:128.0.20240704121409")

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