// Fully qualifying java.util.Properties below does not work: inside an Android
// build script `java` already names the JavaPluginExtension, which shadows the
// package root.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Firebase is optional at build time — the *plugin*, not the SDK.
 *
 * The google-services plugin fails the build outright when google-services.json
 * is missing, and that file is not committed — it identifies a specific Firebase
 * project. Applying the plugin unconditionally would mean nobody could build
 * this repo from a clean clone, which is the one property the README promises.
 * So only the plugin is gated here.
 *
 * The firebase-ai dependency itself stays unconditional, below. It is a plain
 * library with no compile-time need for that file — it only matters at
 * runtime, when FirebaseApp looks for the project resources the plugin
 * generates from it. Gating the dependency too would mean `cloud/` could not
 * compile without a Firebase project, which is a much larger cost for the
 * same safety the plugin gate already buys. BuildConfig.HAS_FIREBASE is what
 * lets the code tell the difference at runtime instead: see
 * app.dewey.cloud.GeminiAnswerComposer and app.dewey.cloud.UnconfiguredAnswerComposer.
 */
val firebaseConfig = file("google-services.json")
val hasFirebase = firebaseConfig.exists()

if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle(
        "Dewey: no app/google-services.json — building without the cloud features. " +
            "See README for how to add one."
    )
}

/**
 * Release signing, if credentials are present.
 *
 * The competition entry is never published to a store, but "download it like a
 * real app" means a signed release build rather than a debug APK — a debug APK
 * is signed with a key every Android developer on earth shares. Credentials live
 * in an uncommitted keystore.properties pointing at a keystore outside the repo.
 *
 * Absent credentials the release variant simply goes unsigned, so a clean clone
 * still builds. The same reasoning as the optional google-services.json.
 */
/**
 * The RevenueCat API key, if there is one.
 *
 * A Test Store key, so it unlocks a sandbox and nothing that costs money — but
 * it is still an account credential in a public repo, so it lives in an
 * uncommitted file beside keystore.properties rather than in source.
 *
 * Absent, the app builds and runs with every feature available: without a
 * purchase system there is nothing to check an entitlement against, and a
 * clean clone should be a working app rather than one locked out of its own
 * best feature. See app.dewey.billing.Entitlements.
 */
val revenueCatProperties = Properties().apply {
    val file = rootProject.file("revenuecat.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val revenueCatKey: String = revenueCatProperties.getProperty("apiKey").orEmpty()

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningCredentials = keystoreProperties.getProperty("storeFile")
    ?.let { file(it).exists() } == true

android {
    namespace = "app.dewey"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dewey"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REVENUECAT_KEY", "\"$revenueCatKey\"")
    }

    signingConfigs {
        if (hasSigningCredentials) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    /**
     * One APK per architecture instead of one carrying all four.
     *
     * ONNX Runtime ships a native library per ABI and they are the largest
     * things in the package: a universal APK is 239MB, of which any given phone
     * uses about a third. Installing an app should not cost someone 150MB of
     * libraries for processors they do not have.
     *
     * x86 (32-bit) is left out — nothing that can run this has one. x86_64 stays
     * because that is what the emulator uses.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            if (hasSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "Dewey: no keystore.properties — the release build will be unsigned."
                )
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")

    androidResources {
        // The encoder is memory-mapped from disk rather than read into the heap,
        // and a compressed asset cannot be mapped. The tokenizer is skipped too:
        // it is already dense binary and compressing it only costs unpack time.
        noCompress += listOf("onnx", "bin")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Room needs the schema on disk so migrations can be diffed and tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.onnxruntime.android)
    implementation(libs.pdfbox.android)

    // Deliberately no firebase-analytics: it is the default suggestion in
    // Firebase's own setup steps, and it would add tracking and consent
    // obligations to an app whose whole argument is about what stays on device.
    //
    // Unconditional — see the comment on `hasFirebase` above for why this one
    // does not need to be gated the way the google-services plugin does.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
}
