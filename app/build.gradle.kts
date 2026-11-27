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
        versionCode = 4
        versionName = "0.4.0"
    }
    buildTypes {
        // The prototype release: not shrunk, because Room, Hilt, WorkManager, sshj and Bouncy Castle find
        // classes by reflection, and not signed; CI publishes it unsigned next to the installable debug build.
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        // The showcase: a debug build that installs next to Ludoform and always shows the placeholder games,
        // so the design can be shared without a library. Its games are invented; nothing in it is a ROM.
        create("showcase") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".showcase"
            versionNameSuffix = "-showcase"
            matchingFallbacks += "debug"
            manifestPlaceholders["appLabel"] = "Ludoform Showcase"
        }
        getByName("debug") { manifestPlaceholders["appLabel"] = "Ludoform" }
        getByName("release") { manifestPlaceholders["appLabel"] = "Ludoform" }
    }
    // The showcase uses the debug build's placeholder cover art.
    sourceSets.getByName("showcase").res.srcDir("src/debug/res")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // sshj, smbj and Bouncy Castle ship the same license and OSGi files.
    packaging {
        resources.excludes +=
            setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
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
