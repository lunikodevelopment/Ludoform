plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lunikodevelopment.ludoform.transfer"
    compileSdk = 35
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:romm"))
    implementation(project(":core:library"))
    implementation(project(":core:media"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Upload servers (D36): SFTP and SMB2/3, with Bouncy Castle for the ciphers Android's stripped provider lacks.
    implementation("com.hierynomus:sshj:0.39.0")
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation(libs.junit)
    testImplementation("org.apache.sshd:sshd-core:2.13.2")
    testImplementation("org.apache.sshd:sshd-sftp:2.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
