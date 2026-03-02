plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm {
        kotlin {
            jvmToolchain(Configurations.jdkVersion)
        }
    }

    androidTarget {
        kotlin {
            jvmToolchain(Configurations.jdkVersion)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bcprov)
            implementation(libs.bcpkix)
            api(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.spake2)
            implementation(libs.documentfile)
            implementation(libs.hiddenapibypass)
        }

        jvmMain.dependencies {
            implementation(libs.spake2)
            implementation(libs.jmdns)
            implementation(libs.junixsocket.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(libs.conscrypt.java)
        }
    }
}

android {
    namespace = "com.flyfishxu.kadb"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
}
