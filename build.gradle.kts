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

    // RFC §3 dependency rule: engine and api must never touch Minecraft.
    if (name == "weft-engine" || name == "weft-api") {
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
