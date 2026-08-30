import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows" / "vercel-deploy.yml"


class VercelDeployWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_requires_explicit_confirmation_and_current_main(self):
        self.assertIn("confirm_production:", self.workflow)
        self.assertIn('[[ "$CONFIRM_PRODUCTION" == "true" ]]', self.workflow)
        self.assertIn('[[ "$GITHUB_REF" == "refs/heads/main" ]]', self.workflow)
        self.assertIn('[[ "$(git rev-parse origin/main)" == "$DEPLOY_SHA" ]]', self.workflow)

    def test_requires_successful_ci_for_the_exact_commit(self):
        verification = self.workflow.split(
            "- name: Verify production request and tested main commit", 1
        )[1].split("- name: Verify required Vercel credentials", 1)[0]

        self.assertIn("--workflow ci.yml", verification)
        self.assertIn('--commit "$DEPLOY_SHA"', verification)
        self.assertIn('.conclusion == "success"', verification)

    def test_credentials_are_selected_per_target(self):
        self.assertIn("VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}", self.workflow)
        self.assertIn("VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}", self.workflow)
        self.assertIn(
            "VERCEL_BACKEND_PROJECT_ID: ${{ secrets.VERCEL_BACKEND_PROJECT_ID }}",
            self.workflow,
        )
        self.assertIn(
            "VERCEL_FRONTEND_PROJECT_ID: ${{ secrets.VERCEL_FRONTEND_PROJECT_ID }}",
            self.workflow,
        )

    def test_uses_pinned_node_and_vercel_cli_versions(self):
        self.assertIn("uses: actions/setup-node@v4", self.workflow)
        self.assertIn("node-version: '22'", self.workflow)
        self.assertIn("npm install --global vercel@59.10.0", self.workflow)

    def test_each_project_is_staged_smoke_tested_then_promoted(self):
        for project in ("backend", "frontend"):
            stage = self.workflow.index(f"- name: Stage {project} production deployment")
            smoke = self.workflow.index(f"- name: Smoke test staged {project}")
            promote = self.workflow.index(f"- name: Promote {project} to production")
            verify = self.workflow.index(f"- name: Verify promoted {project}")
            self.assertLess(stage, smoke)
            self.assertLess(smoke, promote)
            self.assertLess(promote, verify)

        self.assertEqual(2, self.workflow.count("--skip-domain"))
        self.assertEqual(2, self.workflow.count('vercel promote "$DEPLOYMENT_URL"'))

    def test_vercel_curl_uses_environment_auth_without_forwarded_token(self):
        invocations = [
            line.strip()
            for line in self.workflow.splitlines()
            if "vercel " in line and " curl " in line
        ]
        self.assertEqual(5, len(invocations))
        self.assertGreaterEqual(
            self.workflow.count("VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}"),
            len(invocations),
        )
        for invocation in invocations:
            self.assertIn("vercel curl ", invocation)
            self.assertNotIn("--token", invocation)

    def test_backend_security_smoke_test_requires_unauthenticated_401(self):
        backend_smoke = self.workflow.split("- name: Smoke test staged backend", 1)[1]
        backend_smoke = backend_smoke.split("- name: Promote backend to production", 1)[0]

        self.assertIn("/actuator/health", backend_smoke)
        self.assertIn("/api/v1/internal/registry/sync", backend_smoke)
        self.assertIn('[[ "$cron_status" == "401" ]]', backend_smoke)
        separator = "-- " + chr(92)
        self.assertEqual(
            2,
            [line.strip() for line in backend_smoke.splitlines()].count(separator),
        )


if __name__ == "__main__":
    unittest.main()
