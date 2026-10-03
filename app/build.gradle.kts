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
    // Was needed only while GeckoView was a candidate: libxul.so is ~59 MB per 32-bit ABI and the
    // engine shipped it for all three, which pushed the debug APK from 11 MB to a measured 282 MB.
    // GeckoView was removed rather than shipped -- it worked, but a browser that cannot do anything
    // a WebView browser cannot do, at 25 times the size, is not a trade worth making.
    //
    // The block stays, disabled, because it costs nothing and is the right answer the moment a
    // native dependency of that weight is genuinely needed. With only the adblock engine's small
    // .so files, a universal APK is a few hundred KB over the sum of its ABIs.
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

    // GeckoView was evaluated here and removed. The record is kept because the conclusion is
    // non-obvious and will otherwise be re-litigated:
    //
    //   `android.webkit.WebView` has no extension API, so real browser extensions are impossible
    //   without a different engine. GeckoView (Firefox) is the only way to get them as an Android
    //   library rather than a source fork, and it does work -- it resolved, compiled and passed the
    //   full test suite at version 128 without moving the toolchain.
    //
    //   It was removed anyway. The debug APK went from about 11 MB to a measured 282 MB, because
    //   libxul.so is roughly 59 MB per 32-bit ABI and ships for all three. Nothing called into it,
    //   so what that bought was an app that could do exactly what it did before, 25 times larger,
    //   while still having no extensions. Migrating the engine would have taken that further --
    //   2,349 lines of engine/ rewritten -- and the payoff for this app is a password manager and
    //   Dark Reader, since ad blocking is already done with adblock-rust at no size cost.
    //
    //   If extensions are ever wanted, the work is: add the dependency back, replace the engine/
    //   layer with GeckoSession equivalents, and accept the APK size or move to an App Bundle.
    //   Per-ABI GeckoView artifacts exist but cannot be combined -- they all declare the same
    //   Gradle capability -- so a three-ABI project has to use the omni artifact and split the APK.

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