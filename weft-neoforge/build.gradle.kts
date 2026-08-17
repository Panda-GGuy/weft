// weft-neoforge: the actual NeoForge mod. Requires maven.neoforged.net —
// enabled via -PwithNeoForge (see settings.gradle.kts). Built on CI.
plugins {
    id("net.neoforged.moddev") // version resolved in settings.gradle.kts pluginManagement
}

// We reach into the sibling projects' source sets below (mods {} block), so
// they must be configured before this project.
evaluationDependsOn(":weft-engine")
evaluationDependsOn(":weft-api")
evaluationDependsOn(":weft-sandbox")

fun mainSourceSetOf(path: String): SourceSet =
    project(path).extensions.getByType<SourceSetContainer>().getByName("main")

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
            // Dev-run classloading: sibling projects are separate classloader
            // modules unless declared part of this mod — without these, mod
            // construction dies with NoClassDefFoundError on dev.weft.engine.*
            // (the dev-time mirror of the production jar bundling below).
            sourceSet(mainSourceSetOf(":weft-engine"))
            sourceSet(mainSourceSetOf(":weft-api"))
            sourceSet(mainSourceSetOf(":weft-sandbox"))
        }
    }
}

// Pure-Java Weft modules get merged into the mod jar: implementation(project())
// alone only puts them on the dev classpath, so a shipped jar would throw
// NoClassDefFoundError for dev.weft.engine.* in production.
val bundled: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":weft-engine"))
    implementation(project(":weft-api"))
    implementation(project(":weft-sandbox"))
    bundled(project(":weft-engine"))
    bundled(project(":weft-api"))
    bundled(project(":weft-sandbox"))
}

tasks.jar {
    dependsOn(bundled)
    // Callable so the configuration resolves at execution time, after the
    // sibling jars are built.
    from({ bundled.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
