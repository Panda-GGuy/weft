#!/usr/bin/env bash
# Kill -9 during-save recoverability test (RFC-0001 §10 "Chaos" / §12: save
# corruption is the critical risk; kill-tests run in CI from P2 day one).
#
# Boots a NeoForge dedicated server with Weft, forceloads a chunk plate so
# saves are meaty, then repeatedly: trigger `save-all`, kill -9 the server
# java process mid-save, and boot again. The final boot must come up clean —
# no corrupt-chunk/region errors — and stop gracefully.
#
# Weft does not touch the save path yet (increment 1 owns ticking only, and
# vanilla-compatible saves are a design tenet); this harness gates that claim
# NOW so every later increment that goes near saving is already covered.
#
# Requires: bash, python3 (scripts/chaos/rcon.py), a Linux /proc filesystem.
set -u -o pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GAMEDIR="$REPO_ROOT/weft-neoforge/run/server"
RCON_PORT=25575
RCON_PASS="weft-chaos"
ITERATIONS="${WEFT_CHAOS_ITERATIONS:-4}"
BOOT_TIMEOUT_SECONDS=900
KILL_DELAYS_MS=(60 150 300 600)

log() { echo "[chaos] $*"; }

prepare_gamedir() {
  mkdir -p "$GAMEDIR"
  echo "eula=true" > "$GAMEDIR/eula.txt"
  cat > "$GAMEDIR/server.properties" <<EOF
online-mode=false
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$RCON_PASS
level-seed=weft-chaos
view-distance=6
max-tick-time=-1
spawn-protection=0
EOF
}

# The gradle task blocks for the server's lifetime; run it detached and find
# the actual server java process by its working directory (kill -9 must hit
# the game, not gradle).
start_server() {
  local logfile="$1"
  (cd "$REPO_ROOT" && ./gradlew :weft-neoforge:runServer -PwithNeoForge --console=plain \
      > "$logfile" 2>&1) &
  GRADLE_PID=$!
}

server_java_pid() {
  for proc in /proc/[0-9]*; do
    if [ "$(readlink -f "$proc/cwd" 2>/dev/null)" = "$GAMEDIR" ] \
        && grep -qa java "$proc/comm" 2>/dev/null; then
      basename "$proc"
      return 0
    fi
  done
  return 1
}

wait_for_done() {
  local logfile="$1"
  for _ in $(seq 1 "$BOOT_TIMEOUT_SECONDS"); do
    if grep -q 'Done (' "$logfile"; then
      return 0
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null && ! grep -q 'Done (' "$logfile"; then
      log "server exited before reaching Done; tail of log:"
      tail -n 60 "$logfile"
      return 1
    fi
    sleep 1
  done
  log "server did not reach Done within ${BOOT_TIMEOUT_SECONDS}s; tail of log:"
  tail -n 60 "$logfile"
  return 1
}

rcon() {
  python3 "$REPO_ROOT/scripts/chaos/rcon.py" "$RCON_PORT" "$RCON_PASS" "$@"
}

stop_gracefully() {
  rcon stop >/dev/null 2>&1 || true
  wait "$GRADLE_PID" 2>/dev/null || true
}

assert_clean_log() {
  local logfile="$1"
  # Region/chunk recovery failures vanilla logs when a save was torn.
  if grep -iE 'corrupt|failed to (load|read) chunk|exception (reading|loading) chunk|error during save|ChunkLoadingFailure' "$logfile"; then
    log "FAIL: recovery boot logged chunk/region damage (see matches above)"
    return 1
  fi
  return 0
}

main() {
  prepare_gamedir
  mkdir -p "$REPO_ROOT/build/chaos"

  log "boot 0: generate world content, forceload a ~10x10 chunk plate, clean save"
  start_server "$REPO_ROOT/build/chaos/boot0.log"
  wait_for_done "$REPO_ROOT/build/chaos/boot0.log" || exit 1
  rcon forceload add -64 -64 80 80 >/dev/null
  rcon save-all flush >/dev/null
  sleep 2
  stop_gracefully

  for i in $(seq 1 "$ITERATIONS"); do
    local_delay=${KILL_DELAYS_MS[$(( (i - 1) % ${#KILL_DELAYS_MS[@]} ))]}
    logfile="$REPO_ROOT/build/chaos/boot$i.log"
    log "iteration $i/$ITERATIONS: boot, save-all, kill -9 after ${local_delay}ms"
    start_server "$logfile"
    wait_for_done "$logfile" || exit 1
    assert_clean_log "$logfile" || { log "FAIL: damage after iteration $((i-1))"; exit 1; }
    pid="$(server_java_pid)" || { log "FAIL: could not find server java pid"; exit 1; }
    # Touch some state so the save has fresh dirty chunks, then tear mid-save.
    rcon "setblock 8 100 8 minecraft:dirt" >/dev/null 2>&1 || true
    rcon "setblock 8 100 9 minecraft:stone" >/dev/null 2>&1 || true
    rcon save-all >/dev/null 2>&1 || true
    python3 -c "import time; time.sleep($local_delay / 1000.0)"
    log "kill -9 $pid"
    kill -9 "$pid"
    wait "$GRADLE_PID" 2>/dev/null || true
  done

  log "final boot: assert clean recovery after $ITERATIONS torn saves"
  logfile="$REPO_ROOT/build/chaos/final.log"
  start_server "$logfile"
  wait_for_done "$logfile" || { log "FAIL: world did not boot after torn saves"; exit 1; }
  assert_clean_log "$logfile" || exit 1
  stop_gracefully
  assert_clean_log "$logfile" || exit 1
  log "PASS: server recovered cleanly from $ITERATIONS kill -9 torn saves"
}

main "$@"
