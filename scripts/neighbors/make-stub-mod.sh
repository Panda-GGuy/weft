#!/usr/bin/env bash
# Build a stub NeoForge mod jar carrying only a modid (lowcodefml, no code).
# The RFC-0003 R7 neighbor-boot matrix uses these to make ModList report a
# known neighbor as present: posture resolution keys on modid presence, so a
# stub exercises the exact production ladder without downloading real mods
# (no network flake, no version chasing; their code is not what we test).
#
# Usage: make-stub-mod.sh <modid> <output-dir>
set -euo pipefail

MODID="$1"
OUTDIR="$2"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

mkdir -p "$WORKDIR/META-INF" "$OUTDIR"
cat > "$WORKDIR/META-INF/neoforge.mods.toml" <<EOF
modLoader = "lowcodefml"
loaderVersion = "[1,)"
license = "CC0-1.0"

[[mods]]
modId = "$MODID"
version = "1.0.0"
displayName = "R7 stub: $MODID"
description = "Empty stub carrying the modid '$MODID' for Weft's R7 neighbor-boot CI matrix."
EOF

(cd "$WORKDIR" && jar cf "stub-$MODID.jar" META-INF)
mv "$WORKDIR/stub-$MODID.jar" "$OUTDIR/"
echo "$OUTDIR/stub-$MODID.jar"
