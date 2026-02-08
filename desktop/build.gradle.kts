import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(libs.kadb)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.oshi.core)
    runtimeOnly(libs.slf4j.nop)
}

compose.desktop {
    application {
        mainClass = "com.scrctl.desktop.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Scrctl"
            packageVersion = "1.0.0"
            
            windows {
                menuGroup = "Scrctl"
                upgradeUuid = "BF9CDA6A-1391-46D5-9ED5-383D6E68CCEB"
            }
        }
    }
} 