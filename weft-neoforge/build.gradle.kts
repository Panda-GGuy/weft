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
evaluationDependsOn(":weft-services")

fun mainSourceSetOf(path: String): SourceSet =
    project(path).extensions.getByType<SourceSetContainer>().getByName("main")

// WS-8 (RFC-0002): the headless GameTest load generator. Dev-only — the jar
// task below packages main + bundled, so none of this ships in the mod jar.
val gametest: SourceSet = sourceSets.create("gametest")

neoForge {
    version = providers.gradleProperty("neoforge_version").get()

    addModdingDependenciesTo(gametest)

    runs {
        // Fixed game directory: the chaos kill -9 harness and the R7
        // neighbor-boot matrix (scripts/chaos/, scripts/neighbors/) prepare
        // eula/server.properties/mods here and locate the server process by
        // its working directory.
        create("server") {
            server()
            gameDirectory = layout.projectDirectory.dir("run/server")
        }
        create("client") {
            client()
        }
        // Headless benchmark/gametest server: boots the flat seed-0 test
        // world, runs every @GameTest in the "weft" namespace, exits nonzero
        // on failure. WS-8 benchmark JSON lands in run/gametest/weft-bench.json.
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = layout.projectDirectory.dir("run/gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "weft")
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
            sourceSet(mainSourceSetOf(":weft-services"))
            // GameTest classes + the weft:empty template resource must be part
            // of THIS mod so @GameTestHolder scanning and resource lookup see
            // them in dev runs (same classloader rule as the siblings above).
            sourceSet(gametest)
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
    implementation(project(":weft-services"))
    bundled(project(":weft-engine"))
    bundled(project(":weft-api"))
    bundled(project(":weft-sandbox"))
    bundled(project(":weft-services"))
    "gametestImplementation"(sourceSets.main.get().output)
    "gametestImplementation"(project(":weft-engine"))
    "gametestImplementation"(project(":weft-services"))
}

// PR CI compiles the load generator even though only the nightly bench
// workflow runs it — a broken gametest should fail fast, not at 03:17 UTC.
tasks.named("check") {
    dependsOn(tasks.named("compileGametestJava"))
}

tasks.jar {
    dependsOn(bundled)
    // Callable so the configuration resolves at execution time, after the
    // sibling jars are built.
    from({ bundled.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
