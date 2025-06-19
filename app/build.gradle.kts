plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.endproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.endproject"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables.useSupportLibrary = true

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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    implementation(libs.androidx.activity.compose.v182)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)


    implementation(libs.itextpdf.v55133)

    // Firebase
    implementation(platform(libs.firebase.bom.v3273))
    implementation(libs.google.firebase.analytics)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.auth.ktx)

    // ✅ גרסה נכונה של FirebaseUI
    implementation(libs.firebase.ui.auth)

    implementation(libs.itextpdf)


    implementation(libs.google.firebase.storage)



    implementation(libs.androidx.appcompat.v161)
    implementation(libs.material.v1110)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout.v214)
    implementation(libs.play.services.location.v2130)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v115)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
