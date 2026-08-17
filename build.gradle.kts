plugins {
    java
}

subprojects {
    apply(plugin = "java-library")

    group = "dev.weft"
    version = "0.1.0-alpha"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Source files are UTF-8 regardless of platform default (javac on Windows
    // otherwise assumes cp1252 and rejects/mangles non-ASCII).
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // WS-8 (RFC-0002): JMH microbenchmark source set for engine-side modules.
    // Results land in build/reports/jmh/results.json; the nightly bench
    // workflow (.github/workflows/bench.yml) merges them and fails on
    // regression beyond the noise band.
    if (name == "weft-engine" || name == "weft-services") {
        val sourceSetContainer = extensions.getByType<SourceSetContainer>()
        val jmhSet = sourceSetContainer.create("jmh")
        dependencies {
            "jmhImplementation"(sourceSetContainer["main"].output)
            "jmhImplementation"("org.openjdk.jmh:jmh-core:1.37")
            "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
        }
        configurations["jmhImplementation"].extendsFrom(
                configurations["implementation"], configurations["api"])

        // Benchmarks compile on every PR (only the nightly job runs them;
        // this keeps them from bit-rotting in between).
        tasks.named("check") { dependsOn(tasks.named("compileJmhJava")) }

        tasks.register<JavaExec>("jmh") {
            group = "verification"
            description = "Run JMH benchmarks; JSON results to build/reports/jmh/results.json."
            // JMH holds a machine-global lock (/tmp/jmh.lock), and concurrent
            // benchmark JVMs would share CPU and corrupt each other's numbers
            // anyway — serialize the modules' jmh tasks (org.gradle.parallel
            // is on, so `gradle :weft-engine:jmh :weft-services:jmh` would
            // otherwise overlap them; broke the first bench.yml run).
            if (project.name == "weft-services") {
                mustRunAfter(":weft-engine:jmh")
            }
            classpath = jmhSet.runtimeClasspath
            mainClass.set("org.openjdk.jmh.Main")
            val resultFile = layout.buildDirectory.file("reports/jmh/results.json")
            outputs.file(resultFile)
            // A measurement is never "up to date" — numbers, not artifacts.
            outputs.upToDateWhen { false }
            doFirst { resultFile.get().asFile.parentFile.mkdirs() }
            args("-rf", "json", "-rff", resultFile.get().asFile.absolutePath)
            // JavaExec doesn't inherit the project toolchain; pin the launcher
            // like :weft-engine:benchmark does (Gradle itself may run on 17).
            javaLauncher.set((project.extensions.getByName("javaToolchains") as JavaToolchainService)
                    .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        }
    }

    // RFC §3 dependency rule: engine, api, and services must never touch Minecraft.
    if (name == "weft-engine" || name == "weft-api" || name == "weft-services") {
        tasks.register("verifyNoMinecraftImports") {
            val srcDir = layout.projectDirectory.dir("src/main/java")
            inputs.dir(srcDir).withPropertyName("sources").optional()
            doLast {
                val offenders = srcDir.asFileTree.matching { include("**/*.java") }
                    .filter { f ->
                        f.readText().let {
                            it.contains("import net.minecraft") || it.contains("import net.neoforged")
                        }
                    }.files
                if (offenders.isNotEmpty()) {
                    throw GradleException("Minecraft/NeoForge imports forbidden in $name: $offenders")
                }
            }
        }
        tasks.named("check") { dependsOn("verifyNoMinecraftImports") }
    }
}
