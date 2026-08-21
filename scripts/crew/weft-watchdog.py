#!/usr/bin/env python3
"""Bounded watchdog for Weft crew agents and lab server.

Monitors declarative targets from a JSON file. Restarts only when a process is
missing or a positive health probe fails repeatedly. Never treats quiet logs as
proof of a freeze. Uses cooldowns and a restart budget to avoid crash loops.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil


@dataclass
class State:
    failures: int = 0
    restarts: list[float] = field(default_factory=list)
    last_restart: float = 0.0
    disabled_reason: str | None = None


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def process_matches(needle: str) -> list[psutil.Process]:
    matches: list[psutil.Process] = []
    for proc in psutil.process_iter(("pid", "cmdline")):
        try:
            cmd = " ".join(proc.info.get("cmdline") or [])
            if needle.lower() in cmd.lower():
                matches.append(proc)
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue
    return matches


def run_probe(target: dict[str, Any], root: Path) -> tuple[bool, str]:
    probe = target.get("probe")
    if not probe:
        return True, "process present"
    try:
        completed = subprocess.run(
            probe,
            cwd=root,
            shell=True,
            text=True,
            capture_output=True,
            timeout=int(target.get("probeTimeoutSeconds", 15)),
        )
    except subprocess.TimeoutExpired:
        return False, "probe timeout"
    output = (completed.stdout + completed.stderr).strip().replace("\n", " ")[-500:]
    return completed.returncode == 0, output or f"probe rc={completed.returncode}"


def launch(target: dict[str, Any], root: Path, log_dir: Path) -> int:
    command = target["restartCommand"]
    name = target["name"]
    stdout = open(log_dir / f"{name}-watchdog.out.log", "a", encoding="utf-8")
    stderr = open(log_dir / f"{name}-watchdog.err.log", "a", encoding="utf-8")
    flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "CREATE_NO_WINDOW", 0)
    proc = subprocess.Popen(
        command,
        cwd=root,
        shell=True,
        stdout=stdout,
        stderr=stderr,
        creationflags=flags,
    )
    return proc.pid


def append_event(path: Path, target: str, event: str, detail: str) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"at": now_iso(), "target": target, "event": event, "detail": detail}) + "\n")


def free_gb(path: Path) -> float:
    usage = psutil.disk_usage(str(path))
    return usage.free / (1024 ** 3)


def blocking_precondition(config: dict[str, Any], root: Path) -> str | None:
    """Environment faults that make restarts pointless or dangerous.

    Disk exhaustion is the classic false 'freeze': log/save writes fail, ticks
    spike, and a restart only rewrites the same full volume.
    """
    minimum = float(config.get("minFreeDiskGb", 0))
    if minimum <= 0:
        return None
    available = free_gb(root)
    if available < minimum:
        return f"free disk {available:.2f} GB below required {minimum:.2f} GB"
    return None


def evaluate(
    target: dict[str, Any],
    state: State,
    root: Path,
    event_log: Path,
    dry_run: bool,
    precondition: str | None,
) -> None:
    name = target["name"]
    if not target.get("enabled", True) or state.disabled_reason:
        return

    completion_file = target.get("completionFile")
    if completion_file and Path(completion_file).exists():
        # Agent produced its requested final message. Completion is not a stall.
        state.failures = 0
        return

    matches = process_matches(target["processMatch"])
    healthy, detail = (False, "process missing") if not matches else run_probe(target, root)
    if healthy:
        if state.failures:
            append_event(event_log, name, "recovered", detail)
        state.failures = 0
        return

    state.failures += 1
    append_event(event_log, name, "unhealthy", f"failure={state.failures}: {detail}")
    threshold = int(target.get("failureThreshold", 3))
    if state.failures < threshold:
        return

    if precondition:
        # Environment root cause: keep monitoring, do not burn restart budget.
        append_event(event_log, name, "blocked-restart", precondition)
        return

    now = time.time()
    cooldown = int(target.get("cooldownSeconds", 120))
    if now - state.last_restart < cooldown:
        return

    window = int(target.get("restartWindowSeconds", 3600))
    state.restarts = [stamp for stamp in state.restarts if now - stamp < window]
    budget = int(target.get("maxRestartsPerWindow", 3))
    if len(state.restarts) >= budget:
        state.disabled_reason = f"restart budget exhausted ({budget}/{window}s)"
        append_event(event_log, name, "disabled", state.disabled_reason)
        return

    # Missing processes can be relaunched. An unresponsive live server is stopped
    # only after multiple positive probe failures; agent targets normally have no
    # probe and are therefore restarted only when their launcher is absent.
    if matches and target.get("terminateOnFailedProbe", False):
        for proc in matches:
            try:
                proc.terminate()
                proc.wait(timeout=10)
            except (psutil.NoSuchProcess, psutil.TimeoutExpired):
                try:
                    proc.kill()
                except psutil.NoSuchProcess:
                    pass

    if dry_run:
        append_event(event_log, name, "would-restart", target["restartCommand"])
    else:
        pid = launch(target, root, event_log.parent)
        append_event(event_log, name, "restarted", f"pid={pid}; reason={detail}")
    state.restarts.append(now)
    state.last_restart = now
    state.failures = 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    config = json.loads(args.config.read_text(encoding="utf-8"))
    root = Path(config["workspace"]).resolve()
    log_dir = Path(config["logDirectory"]).resolve()
    log_dir.mkdir(parents=True, exist_ok=True)
    event_log = log_dir / "watchdog-events.jsonl"
    states = {target["name"]: State() for target in config["targets"]}
    interval = int(config.get("intervalSeconds", 20))

    append_event(event_log, "watchdog", "started", f"once={args.once}; dryRun={args.dry_run}")
    while True:
        precondition = blocking_precondition(config, root)
        if precondition:
            append_event(event_log, "watchdog", "environment-block", precondition)
        for target in config["targets"]:
            evaluate(target, states[target["name"]], root, event_log, args.dry_run, precondition)
        if args.once:
            break
        time.sleep(interval)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
