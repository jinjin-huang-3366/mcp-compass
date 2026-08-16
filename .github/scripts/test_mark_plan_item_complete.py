import unittest

from mark_plan_item_complete import (
    PlanItemError,
    extract_plan_item,
    mark_plan_item_complete,
)


class ExtractPlanItemTest(unittest.TestCase):
    def test_returns_none_without_marker(self) -> None:
        self.assertIsNone(extract_plan_item("Ordinary pull request body"))

    def test_extracts_exact_item(self) -> None:
        body = "<!-- mcp-compass-plan-item: Add metrics for sync. -->"
        self.assertEqual("Add metrics for sync.", extract_plan_item(body))

    def test_rejects_duplicate_markers(self) -> None:
        body = "\n".join(
            [
                "<!-- mcp-compass-plan-item: First item. -->",
                "<!-- mcp-compass-plan-item: Second item. -->",
            ]
        )
        with self.assertRaises(PlanItemError):
            extract_plan_item(body)

    def test_rejects_malformed_marker(self) -> None:
        with self.assertRaises(PlanItemError):
            extract_plan_item("<!-- mcp-compass-plan-item: missing terminator")

    def test_rejects_valid_and_malformed_markers_together(self) -> None:
        body = "\n".join(
            [
                "<!-- mcp-compass-plan-item: Valid item. -->",
                "<!-- mcp-compass-plan-item: missing terminator",
            ]
        )
        with self.assertRaises(PlanItemError):
            extract_plan_item(body)

    def test_rejects_marker_terminator_in_item(self) -> None:
        with self.assertRaises(PlanItemError):
            mark_plan_item_complete("- [ ] Unsafe --> item.\n", "Unsafe --> item.")


class MarkPlanItemCompleteTest(unittest.TestCase):
    def test_marks_one_exact_item_and_preserves_newline(self) -> None:
        plans = "# Plan\n- [ ] Add incremental `updated_since` sync.\n"
        updated, changed = mark_plan_item_complete(
            plans, "Add incremental `updated_since` sync."
        )
        self.assertTrue(changed)
        self.assertEqual(
            "# Plan\n- [x] Add incremental `updated_since` sync.\n", updated
        )

    def test_already_completed_item_is_idempotent(self) -> None:
        plans = "- [x] Completed item.\n"
        updated, changed = mark_plan_item_complete(plans, "Completed item.")
        self.assertFalse(changed)
        self.assertEqual(plans, updated)

    def test_rejects_missing_item(self) -> None:
        with self.assertRaises(PlanItemError):
            mark_plan_item_complete("- [ ] Another item.\n", "Missing item.")


if __name__ == "__main__":
    unittest.main()
