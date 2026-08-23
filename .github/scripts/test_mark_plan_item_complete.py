import unittest
from pathlib import Path

from mark_plan_item_complete import (
    PlanItemError,
    extract_plan_item,
    format_group_status,
    mark_plan_item_complete,
    update_parallel_group_statuses,
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


class UpdateParallelGroupStatusesTest(unittest.TestCase):
    def test_derives_not_started_in_progress_and_complete_statuses(self) -> None:
        plans = """| Group | Task IDs | Start after | Status |
| --- | --- | --- | --- |
| **PG-01 — complete** | `TASK-01` | None. | Stale |
| **PG-02 — active** | `TASK-01`, `TASK-02` | None. | Stale |
| **PG-03 — waiting** | `TASK-03` | `TASK-02` | Stale |

- [x] **TASK-01** — Done. _(Depends on: none)_
- [ ] **TASK-02** — Active. _(Depends on: TASK-01)_
- [ ] **TASK-03** — Waiting. _(Depends on: TASK-02)_
"""

        updated, changed = update_parallel_group_statuses(plans)

        self.assertTrue(changed)
        self.assertIn("| Complete (1/1) |", updated)
        self.assertIn("| In progress (1/2) |", updated)
        self.assertIn("| Not started (0/1) |", updated)

    def test_is_idempotent_when_statuses_match(self) -> None:
        plans = """| **PG-01 — done** | `TASK-01` | None. | Complete (1/1) |
- [x] **TASK-01** — Done. _(Depends on: none)_
"""

        updated, changed = update_parallel_group_statuses(plans)

        self.assertFalse(changed)
        self.assertEqual(plans, updated)

    def test_rejects_unknown_group_member(self) -> None:
        plans = """| **PG-01 — invalid** | `TASK-02` | None. | Not started (0/1) |
- [x] **TASK-01** — Done. _(Depends on: none)_
"""

        with self.assertRaisesRegex(PlanItemError, "unknown task"):
            update_parallel_group_statuses(plans)

    def test_formats_group_status(self) -> None:
        self.assertEqual("Not started (0/2)", format_group_status(0, 2))
        self.assertEqual("In progress (1/2)", format_group_status(1, 2))
        self.assertEqual("Complete (2/2)", format_group_status(2, 2))

    def test_repository_statuses_match_canonical_tasks(self) -> None:
        plans_path = Path(__file__).resolve().parents[2] / "PLANS.md"
        plans = plans_path.read_text(encoding="utf-8")

        updated, changed = update_parallel_group_statuses(plans)

        self.assertFalse(changed)
        self.assertEqual(plans, updated)


if __name__ == "__main__":
    unittest.main()
