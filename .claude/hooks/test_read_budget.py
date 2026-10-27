"""Tests for read_budget.py. Run: python3 -m unittest discover -s .claude/hooks"""

import json
import os
import subprocess
import sys
import tempfile
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "read_budget.py")


def run(event):
    out = subprocess.run([sys.executable, HOOK], input=json.dumps(event), capture_output=True, text=True, check=True)
    return json.loads(out.stdout)["hookSpecificOutput"]["permissionDecisionReason"] if out.stdout else None


class ReadBudgetTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        for name, lines in (("big.kt", 201), ("edge.kt", 200), ("small.kt", 10)):
            with open(os.path.join(self.dir, name), "w") as f:
                f.write("x\n" * lines)
        os.makedirs(os.path.join(self.dir, "s", "tool-results"))
        with open(os.path.join(self.dir, "s", "tool-results", "b1.txt"), "w") as f:
            f.write("x\n" * 5)

    def read(self, path, **extra):
        return run({"tool_name": "Read", "cwd": self.dir, "tool_input": {"file_path": path, **extra}})

    def bash(self, command, **extra):
        return run({"tool_name": "Bash", "cwd": self.dir, "tool_input": {"command": command}, **extra})

    def test_whole_read_of_big_file_is_denied(self):
        self.assertIn("201 lines", self.read("big.kt"))

    def test_boundary_200_lines_is_allowed(self):
        self.assertIsNone(self.read("edge.kt"))

    def test_ranged_read_is_allowed(self):
        self.assertIsNone(self.read("big.kt", offset=50, limit=200))

    def test_oversized_limit_is_denied(self):
        self.assertIsNotNone(self.read("big.kt", limit=500))

    def test_persisted_output_whole_read_is_denied_even_when_short(self):
        self.assertIn("persisted tool output", self.read(os.path.join(self.dir, "s", "tool-results", "b1.txt")))

    def test_subagent_is_never_limited(self):
        event = {"tool_name": "Read", "cwd": self.dir, "agent_id": "a1", "tool_input": {"file_path": "big.kt"}}
        self.assertIsNone(run(event))

    def test_missing_file_and_images_are_allowed(self):
        self.assertIsNone(self.read("nope.kt"))
        self.assertIsNone(self.read("shot.png"))

    def test_cat_of_big_file_in_pipeline_is_denied(self):
        self.assertIn("big.kt", self.bash("ls; cat small.kt big.kt | head -400"))

    def test_cat_after_cd_resolves_against_new_dir(self):
        os.makedirs(os.path.join(self.dir, "sub"))
        os.rename(os.path.join(self.dir, "big.kt"), os.path.join(self.dir, "sub", "big.kt"))
        self.assertIsNotNone(self.bash("cd sub && cat big.kt"))

    def test_cat_of_small_file_is_allowed(self):
        self.assertIsNone(self.bash("cat small.kt"))

    def test_heredoc_write_is_allowed(self):
        self.assertIsNone(self.bash("cat > big.kt <<'EOF'\ncat big.kt\nEOF"))

    def test_wide_sed_range_is_denied_and_narrow_allowed(self):
        self.assertIsNotNone(self.bash("sed -n 1,400p big.kt"))
        self.assertIsNone(self.bash("sed -n '186,310p' big.kt"))

    def test_bash_in_subagent_is_never_limited(self):
        self.assertIsNone(self.bash("cat big.kt", agent_id="a1"))

    def test_garbage_input_fails_open(self):
        out = subprocess.run([sys.executable, HOOK], input="not json", capture_output=True, text=True)
        self.assertEqual((out.returncode, out.stdout), (0, ""))


if __name__ == "__main__":
    unittest.main()
