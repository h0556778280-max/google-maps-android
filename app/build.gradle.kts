plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.haim.maps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haim.maps"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Debug key keeps CI release APK installable for testing.
            // Replace with a private upload key before publishing to Google Play.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
}
