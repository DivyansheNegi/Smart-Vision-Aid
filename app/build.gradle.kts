plugins {
    alias(libs.plugins.android.application)
}


android {
    namespace = "com.example.smartvisionaid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartvisionaid"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
configurations.all {
    exclude(group = "com.google.ai.edge.litert", module = "litert-api")
}

dependencies {

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")


    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("androidx.core:core-ktx:1.12.0")


    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")


    implementation("androidx.concurrent:concurrent-futures:1.1.0")


    implementation("com.google.guava:guava:31.1-android")
    implementation("com.google.mlkit:face-detection:16.1.6")




    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}