import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

abstract class CopyServerApkToAssetsTask : DefaultTask() {
    // 使用 InputFiles 替代 InputDirectory，规避目录不存在时的验证报错
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverApkFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val outputFileName: Property<String>

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun run() {
        // 在执行时查找最新的 APK
        val apkFile = serverApkFiles.files
            .flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException(
                "No server APK found in provided inputs. Ensure the server module is built correctly."
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

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(Configurations.jdkVersion)
        targetCompatibility = JavaVersion.toVersion(Configurations.jdkVersion)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Configurations.jdkVersion.toString()))
    }
}


androidComponents {
    onVariants(selector().all()) { variant ->
        val buildType = variant.buildType ?: "debug"
        val variantCap = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val buildTypeCap = buildType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val serverProject = project(":server")
        val serverAssembleTaskPath = ":server:assemble$buildTypeCap"

        val copyServerApkTask = tasks.register<CopyServerApkToAssetsTask>("copyServerApkToAssets$variantCap") {
            // 显式依赖 server 的编译任务
            dependsOn(serverAssembleTaskPath)

            // 使用 ConfigurableFileCollection 收集文件
            serverApkFiles.from(serverProject.layout.buildDirectory.dir("outputs/apk/$buildType"))
            outputDir.set(layout.buildDirectory.dir("generated/serverApkAssets/${variant.name}"))
            outputFileName.set("scrcpy-server.jar")
        }

        // 注册生成的 assets 目录
        variant.sources.assets?.addGeneratedSourceDirectory(copyServerApkTask, CopyServerApkToAssetsTask::outputDir)
    }
}

dependencies {
    implementation(project(":kadb"))

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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.kt.compose)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Networking & JSON
    implementation(libs.okhttp3)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
}
