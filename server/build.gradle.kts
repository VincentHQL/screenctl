plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.genymobile.scrcpy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.genymobile.scrcpy"
        minSdk = 21
        targetSdk = 35
        versionCode = 30303
        versionName = "3.3.3"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
}

dependencies {
    implementation(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.aar", "*.jar")
    )))
    testImplementation(libs.junit)
}
