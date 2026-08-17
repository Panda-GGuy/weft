// weft-engine: the loader-agnostic execution engine. No Minecraft imports (enforced).
dependencies {
    api(project(":weft-api"))
}

// Performance benchmark harness (see src/test/.../bench/EngineBenchmark.java).
// Not part of `check` — absolute numbers are machine-dependent; run on demand.
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Benchmark the profiler hot path, report generation, and tick pipeline."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.weft.engine.bench.EngineBenchmark")
    // JavaExec doesn't inherit the project toolchain; without this it runs
    // on Gradle's own JVM (17 here) and dies on class file version.
    javaLauncher.set((project.extensions.getByName("javaToolchains") as JavaToolchainService).launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
