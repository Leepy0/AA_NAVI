plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.leepy0.aanavi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leepy0.aanavi"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
}
