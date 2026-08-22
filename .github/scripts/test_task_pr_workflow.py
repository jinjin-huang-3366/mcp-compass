import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows" / "task-pr.yml"


class TaskPrWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_requires_current_base_before_and_after_validation(self):
        check = "python .github/scripts/require_current_base.py"

        self.assertEqual(2, self.workflow.count(check))
        first_check = self.workflow.index(check)
        backend_validation = self.workflow.index("- name: Run backend tests")
        second_check = self.workflow.rindex(check)
        open_pull_request = self.workflow.index("- name: Open pull request")
        self.assertLess(first_check, backend_validation)
        self.assertLess(backend_validation, second_check)
        self.assertLess(second_check, open_pull_request)

    def test_final_check_fetches_the_latest_requested_base(self):
        recheck = self.workflow.split("- name: Recheck branch against latest base", 1)[1]
        recheck = recheck.split("- name: Open pull request", 1)[0]

        self.assertIn('refs/heads/$BASE_BRANCH:refs/remotes/origin/$BASE_BRANCH', recheck)
        self.assertIn('--base-ref "origin/$BASE_BRANCH"', recheck)


if __name__ == "__main__":
    unittest.main()
