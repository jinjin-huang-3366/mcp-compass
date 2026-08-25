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
        open_pull_request = self.workflow.index("- name: Open or update pull request")
        self.assertLess(first_check, backend_validation)
        self.assertLess(backend_validation, second_check)
        self.assertLess(second_check, open_pull_request)

    def test_final_check_fetches_the_latest_requested_base(self):
        recheck = self.workflow.split("- name: Recheck branch against latest base", 1)[1]
        recheck = recheck.split("- name: Open or update pull request", 1)[0]

        self.assertIn('refs/heads/$BASE_BRANCH:refs/remotes/origin/$BASE_BRANCH', recheck)
        self.assertIn('--base-ref "origin/$BASE_BRANCH"', recheck)

    def test_requires_and_publishes_a_concrete_example(self):
        self.assertIn("      concrete_example:\n", self.workflow)
        self.assertIn("CONCRETE_EXAMPLE: ${{ inputs.concrete_example }}", self.workflow)
        self.assertIn("## Concrete example", self.workflow)
        self.assertIn(
            "CONCRETE_EXAMPLE_PATH: ${{ runner.temp }}/concrete-example.md",
            self.workflow,
        )

    def test_retry_dispatch_updates_the_existing_open_pull_request(self):
        publish = self.workflow.split("- name: Open or update pull request", 1)[1]
        publish = publish.split("- name: Start baseline CI", 1)[0]

        self.assertIn("gh pr list", publish)
        self.assertIn('--head "$BRANCH_NAME"', publish)
        self.assertIn('--state open', publish)
        self.assertIn('gh pr edit "$pr_url"', publish)
        self.assertIn("gh pr create", publish)

    def test_backend_validation_compiles_and_tests_generated_project(self):
        node_setup = self.workflow.index("- name: Set up Node.js")
        backend_validation = self.workflow.index("- name: Run backend tests")

        self.assertLess(node_setup, backend_validation)
        self.assertIn("node-version: '22'", self.workflow)
        self.assertIn(
            "backend/src/main/resources/generator/typescript/v1/package-lock.json",
            self.workflow,
        )
        self.assertIn("MCP_COMPASS_VERIFY_GENERATED_PROJECT: 'true'", self.workflow)
        self.assertIn("MCP_COMPASS_VERIFY_CONTAINER_EXECUTION: 'true'", self.workflow)
        self.assertIn("validation-worker/runtime/typescript-v1/Dockerfile", self.workflow)
        self.assertIn("./mvnw -pl backend,validation-worker test", self.workflow)


if __name__ == "__main__":
    unittest.main()
