plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.itos.coui.icons"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":coui"))
    api(platform("androidx.compose:compose-bom:2026.06.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.runtime:runtime")
}
