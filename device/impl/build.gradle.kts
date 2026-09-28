plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.verlintas.baic2.device.impl"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":device:api"))
    api(project(":core:model"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
