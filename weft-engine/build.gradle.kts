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

// WS-7 gate (RFC-0009 §10.3): the committed event-stream JSON schema is the
// consumer-facing contract, so every emitted kind is validated against it by a
// real JSON Schema implementation rather than by an approximation of one.
// Test scope only — main code keeps zero third-party dependencies.
dependencies {
    testImplementation("com.networknt:json-schema-validator:1.5.8")
}
