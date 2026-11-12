pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // youtubedl-android se publica en JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "PowerPapilo"
include(":app")
