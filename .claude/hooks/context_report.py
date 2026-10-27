#!/usr/bin/env python3
"""Peak context per Claude Code session of this project, the metric AGENTS.md section 19a targets.

Usage: python3 .claude/hooks/context_report.py [N]   (last N sessions, default 10)

Context of a turn = input + cache-read + cache-creation tokens of that assistant message,
taken from the transcripts under ~/.claude/projects/<project>/. Subagent turns are excluded:
they run in their own context.
"""

import glob
import json
import os
import sys


def transcripts_dir(project_dir):
    return os.path.expanduser("~/.claude/projects/" + project_dir.replace("/", "-"))


def session_stats(path):
    contexts = []
    first_prompt = ""
    with open(path) as f:
        for line in f:
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if r.get("isSidechain"):
                continue
            m = r.get("message") or {}
            if r.get("type") == "user" and not first_prompt and isinstance(m.get("content"), str) and not r.get("isMeta"):
                first_prompt = " ".join(m["content"].split())[:70]
            if r.get("type") == "assistant":
                u = m.get("usage") or {}
                t = u.get("input_tokens", 0) + u.get("cache_read_input_tokens", 0) + u.get("cache_creation_input_tokens", 0)
                if t:
                    contexts.append(t)
    subagents = len(glob.glob(path[: -len(".jsonl")] + "/subagents/*.jsonl"))
    return contexts, subagents, first_prompt


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    project = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    paths = sorted(glob.glob(transcripts_dir(project) + "/*.jsonl"), key=os.path.getmtime)[-n:]
    print(f"{'session':10}{'start':>7}{'peak':>7}{'turns':>7}{'subagents':>11}  first prompt")
    for p in paths:
        contexts, subagents, prompt = session_stats(p)
        if contexts:
            print(f"{os.path.basename(p)[:8]:10}{contexts[0] // 1000:>6}k{max(contexts) // 1000:>6}k{len(contexts):>7}{subagents:>11}  {prompt}")


if __name__ == "__main__":
    main()
