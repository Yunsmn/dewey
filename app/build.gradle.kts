plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Firebase is optional at build time.
 *
 * The google-services plugin fails the build outright when google-services.json
 * is missing, and that file is not committed — it identifies a specific Firebase
 * project. Applying the plugin unconditionally would mean nobody could build
 * this repo from a clean clone, which is the one property the README promises.
 *
 * So the free tier builds and runs without it, and only the Librarian's cloud
 * features need it. BuildConfig.HAS_FIREBASE lets the code tell the difference
 * at runtime instead of crashing on a missing default app.
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
    }

    buildTypes {
        release {
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
    if (hasFirebase) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.ai)
    }

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
