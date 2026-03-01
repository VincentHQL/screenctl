plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class CopyAgentApkToAssetsTask : DefaultTask() {
    @get:InputDirectory
    abstract val serverApkDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val outputFileName: Property<String>

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun run() {
        val apkDirFile = serverApkDir.get().asFile
        val apkFile = apkDirFile
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw GradleException(
                "No server APK found in: ${apkDirFile.absolutePath}. Ensure the server module produces an APK for this buildType."
            )

        fileSystemOperations.copy {
            from(apkFile)
            into(outputDir)
            rename { outputFileName.get() }
        }
    }
}

android {
    namespace = "com.scrctl.client"
    compileSdk = Configurations.compileSdk

    defaultConfig {
        applicationId = "com.scrctl.client"
        minSdk = Configurations.minSdk
        targetSdk = Configurations.targetSdk
        versionCode = Configurations.versionCode
        versionName = Configurations.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    sourceSets {
        getByName("main") {
            aidl {
                srcDirs("src/main/aidl")
            }
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val buildType = variant.buildType ?: "debug"
        val variantCap = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val buildTypeCap = buildType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val serverProject = project(":agent")
        val serverAssembleTaskPath = ":agent:assemble$buildTypeCap"

        val copyServerApkTask = tasks.register<CopyAgentApkToAssetsTask>("copyServerApkToAssets$variantCap") {
            dependsOn(serverAssembleTaskPath)

            serverApkDir.set(serverProject.layout.buildDirectory.dir("outputs/apk/$buildType"))
            outputDir.set(layout.buildDirectory.dir("generated/agentApkAssets/${variant.name}"))
            outputFileName.set("agent-server.jar")
        }

        variant.sources.assets?.addGeneratedSourceDirectory(copyServerApkTask, CopyAgentApkToAssetsTask::outputDir)
    }
}

dependencies {
    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // Icons (extended set, used by Home screen view toggles)
    implementation(libs.androidx.material.icons.extended)

    // Image Loader
    implementation(libs.coil.kt.compose)

    // Dependency injection
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(project(":kadb"))
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.okhttp3)
    implementation(libs.okhttp.logging)
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // ADB (Kadb)
    // implementation(libs.kadb)

    // Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Test
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


    implementation(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.aar", "*.jar")
    )))
}
