import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the app version: version.properties at the repo
// root. CHANGELOG.md must contain a matching `## [x.y.z]` heading; CI enforces
// that before a release is published.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("version")
    ?: error("version.properties is missing the 'version' key")
val appVersionCode: Int = versionProps.getProperty("versionCode")?.toIntOrNull()
    ?: error("version.properties is missing a numeric 'versionCode'")

// Release signing. CI restores a keystore and writes keystore.properties next
// to this file; local builds and forks fall back to the debug key so the
// release variant stays installable for sideloading.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.jump.browser"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.jump.browser"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app is English-only; trimming the resource table keeps other
        // locales' annotation tables out of resources.arsc.
        resourceConfigurations += setOf("en")

        // The app ships its own JNI-free UI; the only native code comes from
        // CameraX and Compose. 32-bit x86 is emulator-only, so dropping it
        // removes ~114KB of libraries no real device loads.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Split the APK per ABI in release so a device download carries only its
    // own native libraries.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    packaging {
        resources {
            // Licences and Kotlin metadata are not read at runtime; they were
            // ~23KB of the compressed APK.
            excludes += setOf(
                "META-INF/**",
                "DebugProbesKt.bin",
                "kotlin/**",
                "**/*.kotlin_metadata",
                "**/*.kotlin_builtins",
                "**/LICENSE*",
                "**/NOTICE*",
                "version",
                "**/androidx/*",
            )
        }
        dex {
            // AGP 9 stores the dex uncompressed by default (faster cold start,
            // but ~1.5MB larger on disk). The size budget matters more here,
            // so keep the dex deflated.
            useLegacyPackaging = true
        }
        jniLibs {
            // Extract native libraries at install time instead of mapping them
            // from the APK. Uncompressed, page-aligned libraries put hard
            // alignment constraints on every zip tool in the chain, and a
            // repack step that moved entries was rejected by some installers
            // ("Failed to extract native libraries"). Extracted libraries have
            // no alignment requirement, work on every ROM, and can be
            // compressed — worth more than the mmap overhead for a 10 KB lib.
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
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

    buildFeatures {
        compose = true
    }
}

// Kotlin is compiled by AGP's built-in Kotlin support (AGP 9+); the JVM target
// defaults to android.compileOptions.targetCompatibility, so it is not set here.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    // org.json is stubbed in local tests; use the real implementation so the
    // backup and script persistence code can be exercised.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
