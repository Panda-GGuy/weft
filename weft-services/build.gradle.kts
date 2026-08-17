// weft-services: engine-side services for the RFC-0002 workstreams (WS-1
// activation scheduling, WS-2 async pathfinding). Pure Java 21 — no
// Minecraft imports (enforced by root verifyNoMinecraftImports); the
// NeoForge module supplies entity positions and applies the decisions via
// mixin hooks.

dependencies {
    // WS-2 implements the dev.weft.api.path service surface (pure <- pure).
    implementation(project(":weft-api"))
}
