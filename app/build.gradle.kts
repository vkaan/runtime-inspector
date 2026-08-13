plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vkaan.runtimeinspector.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vkaan.runtimeinspector.app"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":runtimeinspector"))
    // Token's platform API. getLog lives on TSystemServiceBinder; the wrapper needs minSdk 25.
    implementation(files("libs/TSystemWrapper_Version_13_SUNMI_PLATFORM_API.aar"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
