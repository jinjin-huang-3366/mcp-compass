import unittest

from send_pr_email import build_email_content


class BuildEmailContentTest(unittest.TestCase):
    def test_includes_complete_desk_testing_guidance(self) -> None:
        desk_testing = """1. Start PostgreSQL.
2. Start the backend with the local profile.
3. POST to the sync endpoint.

Expected: HTTP 200 and a sync result."""

        content = build_email_content(
            task_description="Expose a local sync endpoint.",
            pr_title="Document local Registry sync",
            pr_url="https://example.test/pull/1",
            branch_name="task/registry-sync-docs",
            codex_summary="Added local development guidance.",
            desk_testing=desk_testing,
        )

        self.assertIn("Desk testing\n------------\n" + desk_testing, content)
        self.assertIn("Validation\n----------", content)
        self.assertIn("Summary\n-------", content)


if __name__ == "__main__":
    unittest.main()
