pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
    // Plugin versions must resolve here (settings context) — a module's
    // plugins{} block cannot read gradle.properties via `providers`.
    plugins {
        id("net.neoforged.moddev") version providers.gradleProperty("moddev_plugin_version").get()
    }
}

rootProject.name = "weft"

include("weft-engine")
include("weft-api")
include("weft-sandbox")

// The NeoForge mod module needs maven.neoforged.net + Minecraft artifacts.
// Enable it explicitly once you're on a normal dev machine:
//   ./gradlew build -PwithNeoForge
if (providers.gradleProperty("withNeoForge").isPresent) {
    include("weft-neoforge")
}
