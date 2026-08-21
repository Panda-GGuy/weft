#!/usr/bin/env python3
"""Generate Codex profile files for every OmniRoute model.

OmniRoute gains/loses providers over time (github and antigravity appeared after
the first crew setup; deepseek and opencode went inactive when quotas lapsed).
Agent failover chains are only useful if a matching `--profile` file exists, so
this script reconciles `~/.codex/<profile>.config.toml` against the live catalog.

Usage:
  python scripts/crew/sync-codex-profiles.py            # create missing profiles
  python scripts/crew/sync-codex-profiles.py --prefix gh
  python scripts/crew/sync-codex-profiles.py --report   # no writes
"""

from __future__ import annotations

import argparse
import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

CODEX_HOME = Path(os.path.expanduser("~")) / ".codex"
MODELS_URL = "http://localhost:20128/v1/models"
PROVIDERS_URL = "http://localhost:20128/api/providers"

# Context windows are advertised per model; fall back to a safe default when the
# catalog omits it so a generated profile never claims more room than it has.
DEFAULT_CONTEXT = 200_000
COMPACT_RATIO = 0.85
TOOL_OUTPUT_LIMIT = 32768


def api_key() -> str:
    key = os.environ.get("OMNIROUTE_API_KEY", "")
    if key:
        return key
    try:
        import winreg  # noqa: PLC0415

        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Environment") as handle:
            value, _ = winreg.QueryValueEx(handle, "OMNIROUTE_API_KEY")
            return value
    except Exception:  # noqa: BLE001
        return ""


def fetch(url: str, key: str) -> dict:
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {key}"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def profile_name(model_id: str) -> str:
    """`cc/claude-sonnet-5` -> `cc-claude-sonnet-5` (matches existing convention)."""
    return re.sub(r"[^A-Za-z0-9]+", "-", model_id).strip("-").lower()


def active_providers(key: str) -> dict[str, bool]:
    try:
        payload = fetch(PROVIDERS_URL, key)
    except Exception:  # noqa: BLE001
        return {}
    return {
        conn.get("provider", ""): bool(conn.get("isActive"))
        for conn in payload.get("connections", [])
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", default="", help="Only sync one prefix, e.g. gh")
    parser.add_argument("--report", action="store_true", help="List actions without writing")
    args = parser.parse_args()

    key = api_key()
    if not key:
        print("OMNIROUTE_API_KEY not set")
        return 2

    try:
        catalog = fetch(MODELS_URL, key)
    except urllib.error.URLError as exc:
        print(f"cannot reach OmniRoute: {exc}")
        return 2

    providers = active_providers(key)
    if providers:
        print("provider state:")
        for name, is_active in sorted(providers.items()):
            print(f"  {name:<14} active={is_active}")

    CODEX_HOME.mkdir(parents=True, exist_ok=True)
    existing = {p.name[: -len(".config.toml")] for p in CODEX_HOME.glob("*.config.toml")}

    created = 0
    skipped = 0
    for entry in catalog.get("data", []):
        model_id = entry.get("id", "")
        # `no-think/...` are derived aliases; the base model already covers them.
        if not model_id or model_id.startswith("no-think/"):
            continue
        if args.prefix and not model_id.startswith(f"{args.prefix}/"):
            continue

        name = profile_name(model_id)
        if name in existing:
            skipped += 1
            continue

        context = int(entry.get("context_length") or DEFAULT_CONTEXT)
        compact = int(context * COMPACT_RATIO)
        body = (
            f"# codex --profile {name}\n"
            f"# {model_id}\n"
            f'model                          = "{model_id}"\n'
            f'model_provider                 = "omniroute"\n'
            f"model_context_window           = {context}\n"
            f"model_auto_compact_token_limit = {compact}\n"
            f"tool_output_token_limit        = {TOOL_OUTPUT_LIMIT}\n"
        )
        if args.report:
            print(f"WOULD CREATE {name}  ({model_id}, ctx={context})")
        else:
            (CODEX_HOME / f"{name}.config.toml").write_text(body, encoding="utf-8")
            print(f"CREATED {name}  ({model_id}, ctx={context})")
        created += 1

    print(f"\ncreated={created} already_present={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
