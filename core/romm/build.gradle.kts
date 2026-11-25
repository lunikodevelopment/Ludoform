plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lunikodevelopment.ludoform.romm"
    compileSdk = 35
    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(project(":core:data"))
    implementation(project(":core:library"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
