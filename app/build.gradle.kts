import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.junit5)
}

// Sketchfab personal API token lives in local.properties (git-ignored):
//   SKETCHFAB_API_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
// Get it from https://sketchfab.com/settings/password ("API token").
// Search works without it; the Download API (Step 6) needs it.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val sketchfabApiToken: String = localProperties.getProperty("SKETCHFAB_API_TOKEN")
    ?: System.getenv("SKETCHFAB_API_TOKEN")
    ?: ""

android {
    namespace = "com.mmunoz.filamentpokemon"
    // androidx.core 1.19 / lifecycle 2.11 / navigation 2.10 require compileSdk >= 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mmunoz.filamentpokemon"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SKETCHFAB_API_TOKEN", "\"$sketchfabApiToken\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    testOptions {
        // Kermit/Log calls reached from JVM unit tests must not throw "not mocked".
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Rendering
    implementation(libs.bundles.filament)

    // Networking / serialization / async
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // DI, images, logging
    implementation(libs.bundles.koin)
    implementation(libs.bundles.coil)
    implementation(libs.kermit)

    // Unit tests (JUnit 5). Gradle 9 requires the platform launcher on the runtime classpath.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
