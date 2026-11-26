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
        maven("https://maven.mozilla.org/maven2/")   // GeckoView
        maven("https://jitpack.io")                  // NewPipeExtractor
    }
}

rootProject.name = "Stube"
include(":app")
include(":benchmark")
