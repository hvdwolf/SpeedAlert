plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "xyz.hvdw.speedalert"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.hvdw.speedalert"
        minSdk = 29
        targetSdk = 33
        versionCode = 6
        versionName = "1.4.1"

        // Only include the ABIs you want in the final APK
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Enable shrinking + minification
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            // Keep debug builds fast
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Remove unwanted native libs if any dependency tries to include them
    packaging {
        jniLibs {
            excludes += listOf(
                "**/armeabi/**",
                "**/armeabi-v7a/**",
                "**/x86/**"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.media:media:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.json:json:20231013")

}
