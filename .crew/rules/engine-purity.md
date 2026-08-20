# Rule: engine purity

Applies when editing weft-engine, weft-api, weft-services.

- No import net.minecraft.* or net.neoforged.*
- Build task verifyNoMinecraftImports must stay green
- Engine talks to the loader through pure interfaces and callbacks owned by weft-neoforge
- Prefer package-stable names under dev.weft.engine.*
- Concurrency primitives need tests; property tests when touching mail/guards
