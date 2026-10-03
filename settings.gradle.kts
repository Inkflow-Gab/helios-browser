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
        // GeckoView is published here and nowhere else. It is not on Maven Central and not on
        // Google Maven, so without this line the dependency simply does not resolve.
        maven { url = uri("https://maven.mozilla.org/maven2") }
    }
}

rootProject.name = "Helios"
include(":app")