pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
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
