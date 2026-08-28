plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

// Static analysis runs over every module from the root so CI has a single entry
// point. Config lives in config/detekt/detekt.yml.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Android modules keep Kotlin under src/main/java by convention; the pure
    // JVM modules use src/main/kotlin. Both are scanned - pointing at only one
    // would silently analyse nothing in half the project.
    source.setFrom(
        files(
            subprojects.flatMap {
                listOf(
                    "${it.projectDir}/src/main/java",
                    "${it.projectDir}/src/main/kotlin",
                )
            },
        ),
    )
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
