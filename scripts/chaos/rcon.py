#!/usr/bin/env python3
"""Minimal RCON client (stdlib only) for the CI chaos/neighbor harnesses.

Usage: rcon.py <port> <password> <command...>
Prints the server's response to stdout; exits nonzero on connection failure.
"""
import socket
import struct
import sys


def send_packet(sock, req_id, ptype, payload):
    body = struct.pack("<ii", req_id, ptype) + payload.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)


def read_packet(sock):
    raw_len = sock.recv(4)
    if len(raw_len) < 4:
        raise ConnectionError("short read on packet length")
    (length,) = struct.unpack("<i", raw_len)
    body = b""
    while len(body) < length:
        chunk = sock.recv(length - len(body))
        if not chunk:
            raise ConnectionError("short read on packet body")
        body += chunk
    req_id, ptype = struct.unpack("<ii", body[:8])
    return req_id, ptype, body[8:-2].decode("utf-8", "replace")


def main():
    port = int(sys.argv[1])
    password = sys.argv[2]
    command = " ".join(sys.argv[3:])
    with socket.create_connection(("127.0.0.1", port), timeout=15) as sock:
        send_packet(sock, 1, 3, password)  # SERVERDATA_AUTH
        req_id, _, _ = read_packet(sock)
        if req_id == -1:
            print("rcon auth failed", file=sys.stderr)
            return 2
        send_packet(sock, 2, 2, command)  # SERVERDATA_EXECCOMMAND
        _, _, response = read_packet(sock)
        print(response)
    return 0


if __name__ == "__main__":
    sys.exit(main())
