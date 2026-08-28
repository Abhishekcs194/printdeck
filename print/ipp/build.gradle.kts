plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.abhishekcs194.printdesk.print.ipp"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(projects.core.model)

    // HP's IPP implementation (Apache-2.0). Speaking IPP directly is what lets
    // us ask the printer what it can actually do, instead of guessing.
    implementation(libs.jipp.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
