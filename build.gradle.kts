buildscript {
    dependencies {
        classpath("androidx.compose.compiler:compiler:1.5.14") // 最新穩定版
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    alias(libs.plugins.compose.compiler) apply false
}
