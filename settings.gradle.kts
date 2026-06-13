pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kaios"

include(
    "runtime-core",
    "tool-runtime",
    "memory-engine",
    "model-providers",
    "kaios-cli",
)
