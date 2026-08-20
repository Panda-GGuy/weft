"""Reproduce RFC-0006 hazard 24: a ticking block entity on a chunk boundary
whose one-block neighbour read crosses into an EVICTED chunk.

Shape, matching the live crash (a vault at the westernmost block of its chunk
reading one block west into an absent chunk):

  1. two permanent forceloaded sites, far apart, so the sections fan out
  2. a third site forceloaded as a 6x6 chunk block
  3. hopper+chest stacks placed on the WESTERNMOST BLOCK of chunk column 2,
     so every setChanged -> updateNeighbourForOutputSignal reads west into
     chunk column 1
  4. chunk column 1 is then force-UNLOADED while the section keeps running,
     evicting exactly the chunk those reads land in
"""
import socket
import struct
import time


def send(s, rid, ptype, payload):
    body = struct.pack("<ii", rid, ptype) + payload.encode("utf-8") + b"\x00\x00"
    s.sendall(struct.pack("<i", len(body)) + body)


def read(s):
    (n,) = struct.unpack("<i", s.recv(4))
    b = b""
    while len(b) < n:
        b += s.recv(n - len(b))
    return b[8:-2].decode("utf-8", "replace")


s = socket.create_connection(("127.0.0.1", 25575), timeout=60)
send(s, 1, 3, "weft-lab")
read(s)


def cmd(c):
    send(s, 2, 2, c)
    return read(s).strip()


# --- 1. two permanent sites, 2000 blocks apart, with load, so buckets fan out
for (x, z) in [(1000, 1000), (3000, 3000)]:
    print(cmd(f"forceload add {x} {z} {x+96} {z+96}"))
    print(cmd(f"fill {x+8} 120 {z+8} {x+71} 120 {z+71} minecraft:hopper replace minecraft:air"))

# --- 2/3/4. the churn site: place boundary block entities, then evict west of them
CHUNK = 16
BASE_X, BASE_Z = 4992, 4992          # chunk 312, 312 (4992 = 312 * 16)
COL1_X0, COL1_X1 = 4992, 5007        # chunk column 312 - the one we will evict
EDGE_X = 5008                        # westernmost block of chunk 313
rounds = 0
deferred_seen = False
t0 = time.time()
while time.time() - t0 < 200:
    cmd(f"forceload add {BASE_X} {BASE_Z} {BASE_X+95} {BASE_Z+95}")
    # Hopper stacks hugging the chunk-313 west edge: chest above (with items to
    # move, so setChanged fires every transfer), hopper on the edge block,
    # chest below to receive.
    for dz in range(0, 80, 8):
        z = BASE_Z + dz
        cmd(f"setblock {EDGE_X} 119 {z} minecraft:chest")
        cmd(f"setblock {EDGE_X} 120 {z} minecraft:hopper")
        cmd(f"setblock {EDGE_X} 121 {z} minecraft:chest{{Items:[{{Slot:0b,id:\"minecraft:stick\",count:64}}]}}")
        cmd(f"setblock {EDGE_X} 122 {z} minecraft:vault")
    time.sleep(3)
    # Evict chunk column 312 - the chunk every one of those reads lands in -
    # while chunk 313 keeps ticking.
    cmd(f"forceload remove {COL1_X0} {BASE_Z} {COL1_X1} {BASE_Z+95}")
    time.sleep(2)
    cmd(f"forceload remove {BASE_X} {BASE_Z} {BASE_X+95} {BASE_Z+95}")
    rounds += 1
    st = cmd("weft status")
    if "read neighbourhood not live" in st:
        deferred_seen = True
    if "hazard" in st.lower():
        print("GUARD TRIPPED:", st)
        break

print(f"rounds={rounds} deferral_counter_seen={deferred_seen}")
print(cmd("weft status"))
print(cmd("save-all"))
print(cmd("stop"))
s.close()
