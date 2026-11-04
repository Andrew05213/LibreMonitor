plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.libremonitor.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "org.libremonitor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.4.1-dev"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    packaging { resources.pickFirsts += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
}

val prepareLicenseAssets by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("docs/REUSE.md"))
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
tasks.named("preBuild").configure { dependsOn(prepareLicenseAssets) }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
