plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The imposition maths lives here and NOTHING else does. No Android, no PdfBox,
// no I/O — just pure functions from settings to an ImpositionPlan. That is what
// makes the booklet/N-up/split geometry exhaustively testable in milliseconds,
// which matters because this is where the subtle bugs live.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.core.model)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
