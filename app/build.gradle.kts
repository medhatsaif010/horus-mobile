plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.horussec.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.horussec.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Test-build signing only (debug builds for the emulator). The test keystore is public on purpose;
    // release builds are signed only with the official key below.
    signingConfigs {
        // Official release key: only on the owner's machine and as a GitHub secret.
        val relFile = System.getenv("HORUS_RELEASE_KEYSTORE_FILE")
        if (relFile != null && file(relFile).exists()) {
            create("horusRelease") {
                storeFile = file(relFile)
                storePassword = System.getenv("HORUS_RELEASE_STORE_PASSWORD")
                keyAlias = "horussec"
                keyPassword = System.getenv("HORUS_RELEASE_STORE_PASSWORD")
            }
        }
        getByName("debug") {
            storeFile = file("horus-test.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        // The debug build is shrunk the same way as the release build, so the emulator test runs the same code.
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Official key only. Without it the release build stays unsigned and cannot be installed,
            // so a build signed with the public test key can never be shipped by mistake.
            signingConfig = signingConfigs.findByName("horusRelease")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
