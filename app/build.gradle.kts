plugins {
    id("com.chaquo.python")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kirinonakar.calcmax"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kirinonakar.calcmax"
        minSdk = 26
        targetSdk = 36
        versionCode = 27
        versionName = "1.2.7"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

chaquopy {
    defaultConfig {
        version = "3.14"
        pip { install("sympy==1.14.0") }
    }
}

dependencies {
    implementation(project(":math"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
