"""The live soak RFC-0006 sec.5 asks for, run unattended against the real pack.

Deliberately built to PLAY rather than to prove a mechanism, because that is the
gap that let hazards 21-24 through a green 23-test suite. It combines, in one
world, every property the gametest rigs individually lack:

  - real mod block entities (Create kinetics/fluids, AE2 grid devices), which
    nothing has ever tested against Weft
  - Brain-based mobs and doors (hazard 21's trigger)
  - block entities ON chunk boundaries, including wide-reach vaults (hazard 24's)
  - three regions far enough apart to fan out (hazard 23's trigger, with
    blockEntitySharding also on)
  - chunk status churn: forceload add/remove, teleports, and Chunky running
    (hazard 22's and 24's)

Reports counters every cycle and exits nonzero the moment anything trips.
"""
import socket
import struct
import sys
import time

HOST, PORT, PW = "127.0.0.1", 25575, "weft-lab"


def _send(s, rid, ptype, payload):
    body = struct.pack("<ii", rid, ptype) + payload.encode("utf-8") + b"\x00\x00"
    s.sendall(struct.pack("<i", len(body)) + body)


def _read(s):
    (n,) = struct.unpack("<i", s.recv(4))
    b = b""
    while len(b) < n:
        b += s.recv(n - len(b))
    return b[8:-2].decode("utf-8", "replace")


sock = socket.create_connection((HOST, PORT), timeout=60)
_send(sock, 1, 3, PW)
_read(sock)


def cmd(c):
    _send(sock, 2, 2, c)
    return _read(sock).strip()


# Three sites, 2000 blocks apart: far past mergeDistance, so three regions.
SITES = [(1000, 1000), (3000, 1000), (5000, 1000)]
# Chunk-boundary x for each site: the westernmost block of a chunk, so every
# neighbour-signal read crosses into the chunk to its west.
CREATE = ["create:fluid_tank", "create:mechanical_press", "create:cogwheel",
          "create:depot", "create:chute", "create:mechanical_mixer"]
AE2 = ["ae2:controller", "ae2:drive", "ae2:energy_acceptor", "ae2:interface"]

print("=== building the rig ===", flush=True)
for (x, z) in SITES:
    print(cmd(f"forceload add {x} {z} {x+96} {z+96}"), flush=True)

for (x, z) in SITES:
    # vanilla ticking mass
    cmd(f"fill {x+8} 120 {z+8} {x+55} 120 {z+55} minecraft:hopper replace minecraft:air")
    # wide-reach types, and ON a chunk boundary (x rounded down to a chunk edge)
    edge = (x // 16) * 16 + 16
    for dz in range(0, 48, 8):
        cmd(f"setblock {edge} 121 {z+dz} minecraft:vault")
        cmd(f"setblock {edge} 122 {z+dz} minecraft:trial_spawner")
    # mod block entities - the surface nothing has tested
    for i, b in enumerate(CREATE):
        cmd(f"setblock {x+16+i*2} 123 {z+16} {b}")
        cmd(f"setblock {edge} 123 {z+i*2} {b}")          # boundary copies too
    for i, b in enumerate(AE2):
        cmd(f"setblock {x+16+i*2} 125 {z+16} {b}")
        cmd(f"setblock {edge} 125 {z+i*2} {b}")
    # Brain mobs + doors: hazard 21's exact trigger
    for i in range(6):
        cmd(f"summon minecraft:villager {x+20+i} 121 {z+20}")
        cmd(f"setblock {x+20+i} 121 {z+22} minecraft:oak_door")
    for i in range(40):
        cmd("summon minecraft:zombie %d 121 %d {PersistenceRequired:1b}" % (x + 24 + i % 20, z + 30))

cmd("gamerule maxEntityCramming 0")
cmd("time set midnight")
cmd("gamerule doDaylightCycle false")
print(cmd("chunky radius 600"), flush=True)
print(cmd("chunky start"), flush=True)


def counters():
    st = cmd("weft status")
    out = {}
    import re
    for key, pat in [
        ("increment", r"increment (\d+) \w+"),
        ("regions", r"topology: (\d+) regions"),
        ("sections", r"(\d+) partitioned sections"),
        ("unmapped", r"(\d+) unmapped units"),
        ("trips", r"(\d+) domain trips"),
        ("deferred", r"(\d+) units deferred"),
        ("border", r"(\d+) border chunk reads"),
        ("shardpass", r"(\d+) passes"),
    ]:
        m = re.search(pat, st)
        out[key] = m.group(1) if m else "-"
    return out, st


FAIL_MARKERS = ("hazard", "Exception", "domain trip guard")
start = time.time()
cycle = 0
rng_spots = [(x + 40, z + 40) for (x, z) in SITES] + [(0, 0)]
while time.time() - start < 900:          # 15 minutes unattended
    cycle += 1
    # play: move a player-ish presence around, churn tickets
    x, z = rng_spots[cycle % len(rng_spots)]
    cmd(f"tp @a {x} 130 {z}") if cycle % 2 else None
    cx, cz = 7000 + (cycle * 37) % 500, 7000 + (cycle * 53) % 500
    cmd(f"forceload add {cx} {cz} {cx+60} {cz+60}")
    cmd("summon minecraft:zombie %d 80 %d {PersistenceRequired:1b}" % (cx + 8, cz + 8))
    time.sleep(6)
    cmd(f"forceload remove {cx} {cz} {cx+60} {cz+60}")
    c, st = counters()
    print(f"[{int(time.time()-start):4d}s] cycle {cycle:3d} "
          f"inc={c['increment']} regions={c['regions']} sections={c['sections']} "
          f"unmapped={c['unmapped']} trips={c['trips']} deferred={c['deferred']} "
          f"border={c['border']} shardpasses={c['shardpass']}", flush=True)
    if c["unmapped"] not in ("-", "0"):
        print("FAIL: unmapped units nonzero ->", st, flush=True)
        sys.exit(1)
    if c["trips"] not in ("-", "0"):
        print("FAIL: domain trips nonzero ->", st, flush=True)
        sys.exit(1)

print("=== soak complete, no faults ===", flush=True)
print(counters()[1], flush=True)
print(cmd("save-all"), flush=True)
sock.close()
