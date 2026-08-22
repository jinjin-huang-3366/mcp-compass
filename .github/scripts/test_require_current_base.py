import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

from require_current_base import main


class RequireCurrentBaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary_directory.name)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test User")
        (self.repo / "shared.txt").write_text("base\n", encoding="utf-8")
        self.git("add", "shared.txt")
        self.git("commit", "-m", "Create base")

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_accepts_task_head_that_contains_base(self):
        self.git("switch", "-c", "task/example")
        (self.repo / "task.txt").write_text("task\n", encoding="utf-8")
        self.git("add", "task.txt")
        self.git("commit", "-m", "Add task")

        result = self.run_check("main", "task/example")

        self.assertEqual(0, result)

    def test_rejects_task_head_after_base_advances(self):
        self.git("switch", "-c", "task/example")
        (self.repo / "task.txt").write_text("task\n", encoding="utf-8")
        self.git("add", "task.txt")
        self.git("commit", "-m", "Add task")
        self.git("switch", "main")
        (self.repo / "shared.txt").write_text("new base\n", encoding="utf-8")
        self.git("add", "shared.txt")
        self.git("commit", "-m", "Advance base")

        result = self.run_check("main", "task/example")

        self.assertEqual(1, result)

    def test_reports_missing_reference_as_invalid_input(self):
        result = self.run_check("missing", "HEAD")

        self.assertEqual(2, result)

    def run_check(self, base_ref: str, head_ref: str) -> int:
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return main(
                [
                    "--repo",
                    str(self.repo),
                    "--base-ref",
                    base_ref,
                    "--head-ref",
                    head_ref,
                ]
            )

    def git(self, *arguments: str) -> None:
        subprocess.run(
            ["git", *arguments],
            cwd=self.repo,
            check=True,
            capture_output=True,
            text=True,
        )


if __name__ == "__main__":
    unittest.main()
