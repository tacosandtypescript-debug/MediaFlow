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

rootProject.name = "MediaFlow"

include(":app")

// Core modules (kept as an empty skeleton, not implemented yet)
include(":core:common")
include(":core:model")
include(":core:ui")
include(":core:media")

// Data / domain layers (kept as an empty skeleton, not implemented yet)
include(":domain")
include(":data")

// Feature modules (kept as an empty skeleton, not implemented yet)
include(":feature:home")
include(":feature:downloads")
include(":feature:gallery")
include(":feature:player")
include(":feature:settings")
