import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "HiALife"; isStatic = true }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.3.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:3.3.3")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.11.0")
            implementation("io.ktor:ktor-client-okhttp:3.3.3")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("io.ktor:ktor-client-okhttp:3.3.3")
            }
        }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.3") }
    }
    jvmToolchain(17)
}

android {
    namespace = "cn.hialife"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.hialife.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "cn.hialife.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "HiALife"
            packageVersion = "1.0.0"
            macOS.bundleID = "cn.hialife.desktop"
        }
    }
}
