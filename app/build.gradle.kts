plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lunikodevelopment.ludoform"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.lunikodevelopment.ludoform"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":shell:display"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:library"))
    implementation(project(":core:romm"))
    implementation(project(":core:transfer"))
    implementation(project(":core:launch"))
    implementation(project(":core:media"))
    implementation(project(":core:theme"))
    implementation(project(":core:input"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
