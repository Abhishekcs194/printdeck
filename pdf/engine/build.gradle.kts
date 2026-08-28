plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.abhishekcs194.printdesk.pdf.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(projects.core.model)
    api(projects.pdf.imposition)

    // PdfBox-Android (Apache-2.0) executes the imposition plan while keeping
    // pages as vector Form XObjects. Rasterisation happens only for preview,
    // via the platform PdfRenderer — never for output.
    implementation(libs.pdfbox.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
