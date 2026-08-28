plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.abhishekcs194.printdesk.print.system"
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
    // Fallback transport: hands the already-imposed PDF to the platform print
    // dialog. The imposition work is baked into the file before either path is
    // chosen, so nothing is lost by falling back.
    api(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
