plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.davidlevi.shabbatwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.davidlevi.shabbatwatch"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    // Fixed, committed keystore so every CI build is signed with the same key and
    // installs as an update over the previous one (same approach as RadioAbba).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug-keystore.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    // Sunset / tzeit, Hebrew date and parsha (LGPL 2.1).
    implementation("com.kosherjava:zmanim:2.5.0")
}
