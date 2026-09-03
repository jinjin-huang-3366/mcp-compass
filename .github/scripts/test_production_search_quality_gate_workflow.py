import unittest
from pathlib import Path


WORKFLOW = (
    Path(__file__).resolve().parents[1]
    / "workflows"
    / "production-search-quality-gate.yml"
)


class ProductionSearchQualityGateWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_runs_the_fixed_rel01_evaluation_without_mutating_labels(self):
        self.assertIn("-Dtest=RegistryRelevanceEvaluationTest test", self.workflow)
        self.assertIn("Recall@100=0.9623, NDCG@10=0.8620", self.workflow)

    def test_targets_promoted_production_through_authenticated_vercel_curl(self):
        self.assertIn("VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}", self.workflow)
        self.assertIn("VERCEL_PROJECT_ID: ${{ secrets.VERCEL_BACKEND_PROJECT_ID }}", self.workflow)
        self.assertIn("vercel curl /actuator/health --", self.workflow)
        self.assertNotIn("--deployment", self.workflow)

    def test_verifies_all_four_named_demo_searches(self):
        for demo_id in (
            "github-no-delete",
            "twilio-sms-no-voice",
            "postgres-read-only",
            "web-docs-readonly",
        ):
            self.assertIn(demo_id, self.workflow)
        self.assertIn("verify_production_demo_searches.py", self.workflow)

    def test_never_uploads_production_responses(self):
        self.assertNotIn("upload-artifact", self.workflow)
        self.assertIn("not uploaded as artifacts", self.workflow)


if __name__ == "__main__":
    unittest.main()
