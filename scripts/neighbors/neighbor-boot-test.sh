#!/usr/bin/env bash
# RFC-0003 R7: "CI boots the neighbors." Boots a dedicated server with a stub
# jar carrying a known neighbor's modid in mods/, waits for the startup
# posture table, and asserts the expected posture line plus a clean boot.
#
# Usage: neighbor-boot-test.sh <modid> <posture-regex...>
#   Each posture-regex is grepped (extended regex) against the server log;
#   all must match. Example:
#     neighbor-boot-test.sh forgia 'regionized_ticking +REFUSED' 'entity_sharding +REFUSED'
set -u -o pipefail

MODID="$1"
shift
EXPECTATIONS=("$@")

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GAMEDIR="$REPO_ROOT/weft-neoforge/run/server"
RCON_PORT=25575
RCON_PASS="weft-r7"
BOOT_TIMEOUT_SECONDS=900
LOGFILE="$REPO_ROOT/build/neighbors/boot-$MODID.log"

log() { echo "[r7:$MODID] $*"; }

mkdir -p "$GAMEDIR/mods" "$REPO_ROOT/build/neighbors"
rm -f "$GAMEDIR"/mods/stub-*.jar
echo "eula=true" > "$GAMEDIR/eula.txt"
cat > "$GAMEDIR/server.properties" <<EOF
online-mode=false
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$RCON_PASS
level-seed=weft-r7
view-distance=4
max-tick-time=-1
EOF

# Optional Weft config for the cell (e.g. enabling a module so a REFUSE
# posture actually resolves; a config-off module has no territory claim).
mkdir -p "$GAMEDIR/config"
rm -f "$GAMEDIR/config/weft-common.toml"
if [ -n "${WEFT_CONFIG_TOML:-}" ]; then
  printf '%s\n' "$WEFT_CONFIG_TOML" > "$GAMEDIR/config/weft-common.toml"
fi

bash "$REPO_ROOT/scripts/neighbors/make-stub-mod.sh" "$MODID" "$GAMEDIR/mods"

(cd "$REPO_ROOT" && ./gradlew :weft-neoforge:runServer -PwithNeoForge --console=plain \
    > "$LOGFILE" 2>&1) &
GRADLE_PID=$!

for _ in $(seq 1 "$BOOT_TIMEOUT_SECONDS"); do
  grep -q 'Done (' "$LOGFILE" && break
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    log "FAIL: server exited before Done; tail:"
    tail -n 80 "$LOGFILE"
    exit 1
  fi
  sleep 1
done
if ! grep -q 'Done (' "$LOGFILE"; then
  log "FAIL: no Done within ${BOOT_TIMEOUT_SECONDS}s"
  tail -n 80 "$LOGFILE"
  exit 1
fi

python3 "$REPO_ROOT/scripts/chaos/rcon.py" "$RCON_PORT" "$RCON_PASS" stop >/dev/null 2>&1 || true
wait "$GRADLE_PID" 2>/dev/null || true

STATUS=0
if ! grep -q "Weft module posture" "$LOGFILE"; then
  log "FAIL: posture table never logged"
  STATUS=1
fi
for expect in "${EXPECTATIONS[@]}"; do
  if grep -qE "$expect" "$LOGFILE"; then
    log "ok: /$expect/"
  else
    log "FAIL: expected posture line matching /$expect/, posture table was:"
    grep -A 10 "Weft module posture" "$LOGFILE" | head -12
    STATUS=1
  fi
done
# Clean boot: a refused module must be a report, never a crash.
if grep -qE 'Fatal|Exception in server tick loop|crash-report' "$LOGFILE"; then
  log "FAIL: boot was not clean"
  STATUS=1
fi
rm -f "$GAMEDIR"/mods/stub-*.jar
[ "$STATUS" = 0 ] && log "PASS"
exit "$STATUS"
