pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Lets modules refer to each other as projects.core.model instead of
// project(":core:model") — compile-checked and refactor-safe.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "PrintDeck"

include(":app")

// Design system and shared domain types.
include(":core:design")
include(":core:model")

// Imposition is split in two on purpose: the maths is pure JVM so it can be
// exhaustively unit-tested without an emulator, and the engine is the thin
// Android layer that executes a plan against PdfBox.
include(":pdf:imposition")
include(":pdf:engine")

// Two printing transports: direct IPP (the point of the app) and the platform
// print dialog as a fallback for printers we cannot reach directly.
include(":print:ipp")
include(":print:system")
