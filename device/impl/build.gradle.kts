plugins {
    alias(libs.plugins.android.library)
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

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
