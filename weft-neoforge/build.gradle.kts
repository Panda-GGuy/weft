// weft-neoforge: the actual NeoForge mod. Requires maven.neoforged.net —
// enabled via -PwithNeoForge (see settings.gradle.kts). Built on CI.
plugins {
    id("net.neoforged.moddev") // version resolved in settings.gradle.kts pluginManagement
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()

    runs {
        create("server") {
            server()
        }
        create("client") {
            client()
        }
    }

    mods {
        create("weft") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":weft-engine"))
    implementation(project(":weft-api"))
    implementation(project(":weft-sandbox"))
}
