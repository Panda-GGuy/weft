#!/usr/bin/env python3
"""Probe Codex profiles and report which routes actually answer.

Failover chains are only trustworthy if the fallback profiles work. This sends a
tiny prompt through each profile and classifies the outcome from the JSON event
stream, so a chain is never built on a provider that 402s/403s/502s.

Usage:
  python scripts/crew/probe-routes.py                      # probe default chain set
  python scripts/crew/probe-routes.py cx-gpt-5-6-sol-high  # probe specific profiles
  python scripts/crew/probe-routes.py --json               # machine-readable
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

CODEX_HOME = Path(os.path.expanduser("~")) / ".codex"
PROBE_PROMPT = "Reply with the single word OK."
TIMEOUT = 90

# Distinguish "route is dead" from "model answered". Ordered: first match wins.
DEAD_PATTERNS = [
    (r"No active credentials", "no-credentials"),
    (r"\b402\b|Insufficient Balance|Payment Required", "billing"),
    (r"\b403\b|Forbidden|blocked by Vercel", "forbidden"),
    (r"\b401\b|Unauthorized|invalid_api_key", "unauthorized"),
    (r"\b429\b|rate limit|quota", "rate-limited"),
    (r"\b50[023]\b|Bad Gateway|Service Unavailable", "upstream-5xx"),
    (r"stream disconnected|stream closed before", "stream-broken"),
    (r"model .*not (supported|found)", "model-missing"),
]

DEFAULT_PROFILES = [
    # codex
    "cx-gpt-5-6-sol-high",
    # claude
    "cc-claude-sonnet-5",
    "cc-claude-opus-5",
    "cc-claude-fable-5",
    # github (new)
    "gh-claude-sonnet-5",
    "gh-claude-opus-5",
    "gh-gpt-5-6-sol",
    "gh-claude-sonnet-4-6",
    # antigravity (new)
    "aug-sonnet5-500k",
    "aug-opus4-8",
    "aug-gpt5-6-sol",
    # xai / grok-cli
    "xao-grok-4-5",
    "xao-grok-4-20-0309-reasoning",
    "gc-grok-4-5",
    "gc-grok-4-6",
    # tllm
    "tllm-claude-4-6-sonnet",
    # disabled pools, probed to detect quota recovery
    "ds-deepseek-v4-pro",
    "oc-claude-sonnet-5",
]


def codex_entrypoint() -> tuple[str, str]:
    codex = shutil.which("codex")
    if not codex:
        raise SystemExit("codex not on PATH")
    npm_root = Path(codex).parent
    codex_js = npm_root / "node_modules" / "@openai" / "codex" / "bin" / "codex.js"
    node = shutil.which("node") or str(npm_root / "node.exe")
    if not codex_js.exists():
        raise SystemExit(f"missing {codex_js}")
    return node, str(codex_js)


def classify(stream: str) -> tuple[str, str]:
    """Return (status, detail). Completion wins over transient reconnect noise."""
    completed = '"type":"turn.completed"' in stream or '"type":"agent_message"' in stream
    failed = '"type":"turn.failed"' in stream

    if completed and not failed:
        return "OK", ""
    for pattern, label in DEAD_PATTERNS:
        if re.search(pattern, stream, re.IGNORECASE):
            # A model that recovered after reconnects still counts as usable.
            return ("OK" if completed else "DEAD"), label
    if completed:
        return "OK", ""
    if failed:
        return "DEAD", "turn-failed"
    return "UNKNOWN", "no-terminal-event"


def probe(node: str, codex_js: str, profile: str, cwd: Path) -> dict:
    if not (CODEX_HOME / f"{profile}.config.toml").exists():
        return {"profile": profile, "status": "MISSING", "detail": "no profile file"}
    try:
        proc = subprocess.run(
            [
                node, codex_js, "exec",
                "--profile", profile,
                "-C", str(cwd),
                "-s", "read-only",
                "--json", "-",
            ],
            input=PROBE_PROMPT,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=TIMEOUT,
        )
    except subprocess.TimeoutExpired:
        return {"profile": profile, "status": "DEAD", "detail": "timeout"}
    stream = (proc.stdout or "") + (proc.stderr or "")
    status, detail = classify(stream)
    return {"profile": profile, "status": status, "detail": detail, "exit": proc.returncode}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profiles", nargs="*", default=None)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    node, codex_js = codex_entrypoint()
    cwd = Path(__file__).resolve().parents[2]
    targets = args.profiles or DEFAULT_PROFILES

    results = []
    for profile in targets:
        result = probe(node, codex_js, profile, cwd)
        results.append(result)
        if not args.json:
            detail = f"  ({result['detail']})" if result.get("detail") else ""
            print(f"{result['status']:<8} {profile}{detail}", flush=True)

    if args.json:
        print(json.dumps(results, indent=2))
    else:
        usable = [r["profile"] for r in results if r["status"] == "OK"]
        print(f"\nusable: {len(usable)}/{len(results)}")
        for profile in usable:
            print(f"  {profile}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
