import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no separate kotlin-android plugin is applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/*
 * Versioning: the single source of truth is `gyro.versionName` in gradle.properties. The version
 * code is derived from it, so a release only ever needs that one line changed (and a matching
 * git tag, which the release workflow checks).
 *
 *   1.4.2         -> 1_04_02_99   (final releases end in 99)
 *   1.5.0-beta.3  -> 1_05_00_03   (pre-releases sort before their final release)
 */
val appVersionName: String = providers.gradleProperty("gyro.versionName").get()

fun versionCodeOf(name: String): Int {
    val match = requireNotNull(Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z-]+(?:\.(\d+))?)?$""").matchEntire(name)) {
        "gyro.versionName must look like 1.2.3 or 1.2.3-beta.4, was '$name'"
    }
    val (major, minor, patch) = match.destructured
    val preRelease = match.groupValues[4]
    val isPreRelease = name.contains('-')
    require(minor.toInt() < 100 && patch.toInt() < 100) { "Minor and patch must be below 100" }
    val suffix = if (isPreRelease) (preRelease.toIntOrNull() ?: 0).coerceIn(0, 98) else 99
    return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + suffix
}

/*
 * Release signing. Values come from keystore.properties in the project root (never committed;
 * see keystore.properties.example) or, on CI, from environment variables. Without them,
 * assembleRelease still works but produces an unsigned APK.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "GYRO_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "GYRO_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "GYRO_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "GYRO_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "com.gyro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gyro.app"
        minSdk = 33
        targetSdk = 36
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release-speed build you can install straight from Android Studio (Build Variants → "profile").
        // Debug builds of Compose apps are much slower than release (debuggable ART, no R8), so judge
        // smoothness with this variant, not with debug.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
