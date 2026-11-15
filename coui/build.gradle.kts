plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.itos.coui"
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
        optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
        optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
    }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2026.06.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.animation:animation")
    api("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.core:core-ktx:1.16.0")
}
