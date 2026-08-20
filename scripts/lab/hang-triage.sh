#!/usr/bin/env bash
# Is the server HUNG or DEAD? (TESTING-0001 §3.1/§3.2)
#
# They look identical to a player and lead to opposite investigations. The
# metrics exporter answers on its own thread, so it keeps serving while the tick
# loop is dead: a frozen tick counter with a live endpoint is a hang, and a gone
# endpoint is a gone process. A hang also leaves NO crash report, which is how
# one got reported as a crash and wasted the first ten minutes.
#
# On a hang this dumps twice. Two dumps naming the same awaited object is the
# difference between "seems stuck" and "provably deadlocked, here it is".
set -uo pipefail
PORT="${WEFT_METRICS_PORT:-9940}"
JSTACK="${JSTACK:-/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot/bin/jstack.exe}"
OUT="${1:-./hang-triage}"
tick() { curl -s --max-time 5 "http://127.0.0.1:$PORT/metrics" 2>/dev/null \
         | grep -m1 '^weft_tick_period_seconds_count' | awk '{print $2}'; }
a="$(tick)"
if [ -z "$a" ]; then echo "endpoint down -> process gone, or metrics off. Look for a crash report."; exit 0; fi
sleep 6
b="$(tick)"
if [ "$a" != "$b" ]; then echo "ticking normally ($a -> $b). Not a hang."; exit 0; fi
echo "TICK COUNTER FROZEN at $a with the exporter alive -> hang, not crash."
# jps will NOT list a launcher's own JVM; match on the command line instead.
pid="$(powershell.exe -NoProfile -Command \
  "(Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' or Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*minecraft*' } | Select-Object -First 1).ProcessId" \
  2>/dev/null | tr -d '\r\n ')"
[ -z "$pid" ] && { echo "could not locate the JVM"; exit 1; }
mkdir -p "$OUT"
"$JSTACK" -l "$pid" > "$OUT/dump1.txt" 2>&1; sleep 20
"$JSTACK" -l "$pid" > "$OUT/dump2.txt" 2>&1
echo "dumps in $OUT. Server thread:"
awk '/^"Server thread"/{f=1} f&&/^$/{exit} f' "$OUT/dump1.txt" | grep -E "Thread.State|dev\.weft|parking to wait"
echo "--- same awaited object in both dumps means a PERMANENT hang ---"
for f in "$OUT/dump1.txt" "$OUT/dump2.txt"; do
  grep -A3 '^"Server thread"' "$f" | grep "parking to wait" || echo "  (not parked in $f)"
done
