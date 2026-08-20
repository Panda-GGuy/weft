"""A player-movement proxy for the real-pack lab, where a real bot cannot join.

WHY THIS EXISTS
---------------
Two lab profiles are possible and neither subsumes the other:

  Weft-only + a mineflayer bot   real player tickets, frontier chunk loading,
                                 packets, block interaction - but no mods, so no
                                 mod compatibility coverage at all.
  Real pack + rcon               Create/AE2 block entities, mixin conflicts,
                                 load-time compat - but NO PLAYER, because
                                 NeoForge rejects a vanilla-protocol client the
                                 moment a client-required mod is installed:
                                 "You are trying to connect to a server that is
                                 running NeoForge, but you are not."

That rejection is correct behaviour and not worth fighting. What it costs is the
single most productive property a player has, server-side: a **moving chunk
frontier**. A walking player continuously promotes chunks ahead of them and
releases chunks behind them, and chunk status changing underneath a running tick
section is exactly the mechanism behind RFC-0006 hazards 22 and 24.

This reproduces that frontier with a sliding forceload window: a ring of chunks
that advances along a path, dropping the trailing edge as it adds the leading
one. Same load/unload churn, no client required.

WHAT IT DOES NOT REPRODUCE - be honest about this when reading a green result:
  - entity tracking and packet sends (no client to send to)
  - the container-open path (LevelChunk.getBlockEntity's mutating read, which is
    where hazards 17 and 18 lived) - hoppers exercise it incidentally, a player
    opening a chest exercises it directly
  - integrated-server semantics: single player runs the server inside the client
    process, and the CLIENT thread also ticks levels. That is a genuinely
    different threading environment and this lab cannot enter it.
  - anything client-side: rendering, Sodium/Iris, shader interactions

So: use this for churn and stress, use the Weft-only + bot profile for player
behaviour, and neither replaces someone actually playing.
"""
import math
import socket
import struct
import sys
import time

HOST, PORT, PW = "127.0.0.1", 25575, "weft-lab"
# Matches a typical single-player simulation distance: the forceload ring a
# walking player's ticket effectively maintains.
RADIUS_CHUNKS = 5
STEP_BLOCKS = 48          # how far the "player" moves per step
STEP_SECONDS = 2.0        # pace; a sprinting player covers ~5.6 blocks/s


def _send(s, rid, ptype, payload):
    body = struct.pack("<ii", rid, ptype) + payload.encode("utf-8") + b"\x00\x00"
    s.sendall(struct.pack("<i", len(body)) + body)


def _read(s):
    (n,) = struct.unpack("<i", s.recv(4))
    b = b""
    while len(b) < n:
        b += s.recv(n - len(b))
    return b[8:-2].decode("utf-8", "replace")


def connect():
    s = socket.create_connection((HOST, PORT), timeout=60)
    _send(s, 1, 3, PW)
    _read(s)
    return s


def walk(sock, seconds=300.0, centre=(0, 0), loop_radius=1200):
    """Slide a forceload ring along a circular path for `seconds`.

    A circle rather than a line so the walk stays within a bounded area and
    revisits ground - which matters, because re-promoting a chunk that was
    recently released is a different code path from promoting a fresh one.
    """
    def cmd(c):
        _send(sock, 2, 2, c)
        return _read(sock).strip()

    held = None
    t0 = time.time()
    steps = 0
    circumference = 2 * math.pi * loop_radius
    while time.time() - t0 < seconds:
        theta = (steps * STEP_BLOCKS / circumference) * 2 * math.pi
        x = int(centre[0] + math.cos(theta) * loop_radius)
        z = int(centre[1] + math.sin(theta) * loop_radius)
        span = RADIUS_CHUNKS * 16
        cmd(f"forceload add {x - span} {z - span} {x + span} {z + span}")
        if held is not None:
            hx, hz = held
            # Release the trailing ring only where it no longer overlaps the new
            # one, so the frontier slides instead of flickering off and on.
            if abs(hx - x) > span or abs(hz - z) > span:
                cmd(f"forceload remove {hx - span} {hz - span} {hx + span} {hz + span}")
        held = (x, z)
        steps += 1
        time.sleep(STEP_SECONDS)
    if held is not None:
        hx, hz = held
        cmd(f"forceload remove {hx - span} {hz - span} {hx + span} {hz + span}")
    return steps


if __name__ == "__main__":
    secs = float(sys.argv[1]) if len(sys.argv) > 1 else 300.0
    s = connect()
    print(f"walking a {RADIUS_CHUNKS}-chunk frontier for {secs:.0f}s", flush=True)
    n = walk(s, secs)
    print(f"{n} steps", flush=True)
    _send(s, 2, 2, "weft status")
    print(_read(s).strip(), flush=True)
    s.close()
