#!/usr/bin/env python3
"""PreToolUse hook: keep whole-file reads out of the orchestrator's context.

Transcripts of the /implement runs showed the main session reading entire source files
and re-reading persisted tool outputs in full (30-50k tokens per run), then carrying them
for every later turn. This hook denies those reads in the main session only, with a reason
that names the cheaper route. Subagents (hook input carries `agent_id`) are never limited:
reading is their job, and their context is thrown away.

Fails open: any error, unknown tool or unreadable file allows the call.
"""

import json
import os
import re
import shlex
import sys

MAX_LINES = 200
BINARY_SUFFIXES = (".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".ipynb")
PERSISTED_OUTPUT = "/tool-results/"


def line_count(path):
    try:
        with open(path, "rb") as f:
            return sum(1 for _ in f)
    except OSError:
        return None


def advice(target, lines):
    return (
        f"read budget: {target} is {lines} lines and this is the orchestrator session, where every line "
        f"is re-sent on every later turn. Locate first (grep -n), then read at most {MAX_LINES} lines "
        f"(Read with offset/limit, or sed -n 'a,bp'). To understand a module, delegate to mini-explorer "
        f"and ask for a short brief. See AGENTS.md section 19a."
    )


def check_read(tool_input, cwd):
    path = tool_input.get("file_path") or ""
    if not path or path.lower().endswith(BINARY_SUFFIXES):
        return None
    limit = tool_input.get("limit")
    if limit is not None and int(limit) <= MAX_LINES:
        return None
    if PERSISTED_OUTPUT in path:
        return (
            f"read budget: {path} is a persisted tool output; reading it whole puts the output you just "
            f"avoided back into context. grep it, read a {MAX_LINES}-line range, or rerun the command narrower."
        )
    lines = line_count(os.path.join(cwd, path))
    if lines is not None and lines > MAX_LINES:
        return advice(path, lines)
    return None


SEGMENT_SPLIT = re.compile(r"\|\||&&|[|;\n]")
SED_RANGE = re.compile(r"^'?(\d+),(\d+)p'?$")


def file_args(tokens):
    return [t for t in tokens[1:] if not t.startswith("-")]


def check_bash(tool_input, cwd):
    command = tool_input.get("command") or ""
    # Heredocs carry file contents being written, not read.
    command = command.split("<<", 1)[0]
    for segment in SEGMENT_SPLIT.split(command):
        try:
            tokens = shlex.split(segment)
        except ValueError:
            continue
        while tokens and re.match(r"^\w+=", tokens[0]):
            tokens = tokens[1:]
        if not tokens or any(t.startswith(">") for t in tokens):
            continue
        if tokens[0] == "cd" and len(tokens) > 1:
            cwd = os.path.join(cwd, os.path.expanduser(tokens[1]))
        elif tokens[0] == "cat":
            for arg in file_args(tokens):
                lines = line_count(os.path.join(cwd, arg))
                if lines is not None and lines > MAX_LINES:
                    return advice(arg, lines)
        elif tokens[0] == "sed" and "-n" in tokens:
            for t in tokens:
                m = SED_RANGE.match(t)
                if m and int(m.group(2)) - int(m.group(1)) + 1 > MAX_LINES:
                    return (
                        f"read budget: sed range {m.group(1)},{m.group(2)} is over {MAX_LINES} lines in the "
                        f"orchestrator session. Narrow it, or delegate the reading to mini-explorer."
                    )
    return None


def decide(event):
    if event.get("agent_id"):
        return None
    cwd = event.get("cwd") or os.getcwd()
    tool_input = event.get("tool_input") or {}
    name = event.get("tool_name")
    if name == "Read":
        return check_read(tool_input, cwd)
    if name == "Bash":
        return check_bash(tool_input, cwd)
    return None


def main():
    try:
        reason = decide(json.load(sys.stdin))
    except Exception:
        return 0
    if reason:
        json.dump(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                }
            },
            sys.stdout,
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
