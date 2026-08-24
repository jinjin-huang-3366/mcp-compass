import re
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows" / "ci.yml"


class CiWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_runs_for_main_pushes_pull_requests_and_manual_dispatches(self):
        self.assertRegex(self.workflow, r"(?m)^  push:\n    branches: \[main\]$")
        self.assertRegex(self.workflow, r"(?m)^  pull_request:$")
        self.assertRegex(self.workflow, r"(?m)^  workflow_dispatch:$")

    def test_backend_job_runs_java_21_maven_tests(self):
        backend = self.job("backend")

        self.assertIn("actions/setup-java@v4", backend)
        self.assertIn("java-version: '21'", backend)
        self.assertIn("actions/setup-node@v4", backend)
        self.assertIn("node-version: '22'", backend)
        self.assertIn(
            "cache-dependency-path: backend/src/main/resources/generator/typescript/v1/package-lock.json",
            backend,
        )
        self.assertIn("MCP_COMPASS_VERIFY_GENERATED_PROJECT: 'true'", backend)
        self.assertIn("run: ./mvnw -pl backend test", backend)

    def test_web_job_uses_node_22_clean_install_lint_and_build(self):
        web = self.job("web")

        self.assertIn("actions/setup-node@v4", web)
        self.assertIn("node-version: '22'", web)
        commands = ["run: npm ci", "run: npm run lint", "run: npm run build"]
        positions = [web.index(command) for command in commands]
        self.assertEqual(positions, sorted(positions))

    def test_workflow_automation_tests_remain_a_ci_gate(self):
        automation = self.job("automation")

        self.assertIn(
            "run: python -m unittest discover -s .github/scripts -p 'test_*.py'",
            automation,
        )

    def job(self, name):
        match = re.search(
            rf"(?ms)^  {re.escape(name)}:\n(.*?)(?=^  [a-zA-Z0-9_-]+:\n|\Z)",
            self.workflow,
        )
        self.assertIsNotNone(match, f"missing {name} job")
        return match.group(1)


if __name__ == "__main__":
    unittest.main()
