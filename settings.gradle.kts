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
    }
}
rootProject.name = "Ludoform"
include(
    ":app",
    ":shell:display",
    ":core:model",
    ":core:data",
    ":core:library",
    ":core:romm",
    ":core:transfer",
    ":core:launch",
    ":core:media",
    ":core:theme",
    ":core:input",
)
