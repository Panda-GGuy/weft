#!/usr/bin/env bash
# Copy a real pack's SERVER-CAPABLE mods into the dev server, so a soak exercises
# the mods Weft exists to run rather than Weft alone (TESTING-0001 §2.3).
#
# Usage: scripts/lab/install-pack.sh <instance-dir>
#
# Deliberately excludes:
#   weft-*        the dev run loads Weft from source; a jar too means a duplicate modid
#   sodium|iris   client-only, and they crash a dedicated server
#   *.disabled    respect the operator's choice
#   moonrise      RFC-0006 hazard 20: it replaces the chunk system Weft's worker
#                 read path is written against. Opt in deliberately, not by a glob.
set -euo pipefail
SRC="${1:?usage: install-pack.sh <instance-dir>}/mods"
DEST="weft-neoforge/run/server/mods"
mkdir -p "$DEST"
for jar in "$SRC"/*.jar; do
  name="$(basename "$jar")"
  case "$name" in
    weft-*|*sodium*|*iris*|*[Mm]oonrise*) echo "skip  $name"; continue ;;
  esac
  cp "$jar" "$DEST/$name" && echo "copy  $name"
done
echo
echo "Installed into $DEST. Boot with:"
echo "  ./gradlew :weft-neoforge:runServer -PwithNeoForge"
