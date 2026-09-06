import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows" / "production-relevance.yml"


class ProductionRelevanceWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_accepts_backend_supported_registry_page_bound(self):
        self.assertIn(
            "MAX_REGISTRY_PAGES >= 1 && MAX_REGISTRY_PAGES <= 100",
            self.workflow,
        )
        self.assertIn(
            "max_registry_pages must be between 1 and 100.",
            self.workflow,
        )

    def test_keeps_production_confirmation_and_bounded_maintenance_call(self):
        self.assertIn('[[ "$CONFIRM_PRODUCTION" == "true" ]]', self.workflow)
        self.assertIn(
            "activate?maxPages=$MAX_REGISTRY_PAGES&embeddingBatchSize=$EMBEDDING_BATCH_SIZE",
            self.workflow,
        )


if __name__ == "__main__":
    unittest.main()
