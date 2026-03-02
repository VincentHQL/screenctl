plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.scrctl.agent"
    compileSdk = Configurations.compileSdk

    defaultConfig {
        applicationId = "com.scrctl.agent"
        minSdk = Configurations.minSdk
        targetSdk = Configurations.targetSdk
        versionCode = Configurations.versionCode
        versionName = Configurations.versionName
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
        sourceCompatibility = JavaVersion.toVersion(Configurations.jdkVersion)
        targetCompatibility = JavaVersion.toVersion(Configurations.jdkVersion)
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
