import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("resolve_group.py")
SPEC = importlib.util.spec_from_file_location("resolve_group", MODULE_PATH)
RESOLVER = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = RESOLVER
SPEC.loader.exec_module(RESOLVER)


PLAN = """# Plan

| Group | Task IDs | Start after |
| --- | --- | --- |
| **PG-01 — ready work** | `TASK-02`, `TASK-03` | `TASK-01` |
| **PG-02 — later work** | `TASK-04` | `TASK-02` |

- [x] **TASK-01** — Foundation. _(Depends on: none)_
- [ ] **TASK-02** — First ready task. _(Depends on: TASK-01)_
- [ ] **TASK-03** — Second ready task. _(Depends on: TASK-01)_
- [ ] **TASK-04** — Blocked task. _(Depends on: TASK-02)_
"""


class ResolveGroupTest(unittest.TestCase):
    def test_resolves_ready_group_to_exact_single_task_inputs(self):
        result = RESOLVER.resolve_group(PLAN, "pg-01")

        self.assertEqual("ready", result["status"])
        self.assertEqual(["TASK-02", "TASK-03"], result["ready_task_ids"])
        first = result["tasks"][0]
        self.assertEqual("task/task-02", first["branch_name"])
        self.assertEqual(
            "**TASK-02** — First ready task. _(Depends on: TASK-01)_",
            first["plan_item"],
        )
        self.assertFalse(first["plan_item"].startswith("- [ ]"))

    def test_reports_blocked_group(self):
        result = RESOLVER.resolve_group(PLAN, "PG-02")

        self.assertEqual("blocked", result["status"])
        self.assertEqual(["TASK-04"], result["blocked_task_ids"])
        self.assertEqual(["TASK-02"], result["tasks"][0]["blocked_by"])

    def test_skips_completed_group_members(self):
        plan = PLAN.replace("- [ ] **TASK-02**", "- [x] **TASK-02**")

        result = RESOLVER.resolve_group(plan, "PG-01")

        self.assertEqual("ready", result["status"])
        self.assertEqual(["TASK-02"], result["completed_task_ids"])
        self.assertEqual(["TASK-03"], result["ready_task_ids"])

    def test_rejects_same_group_dependency(self):
        plan = PLAN.replace(
            "**TASK-03** — Second ready task. _(Depends on: TASK-01)_",
            "**TASK-03** — Second ready task. _(Depends on: TASK-02)_",
        )

        with self.assertRaisesRegex(RESOLVER.PlanError, "same-group"):
            RESOLVER.resolve_group(plan, "PG-01")

    def test_rejects_unchecked_task_missing_from_groups(self):
        plan = PLAN.replace("`TASK-02`, `TASK-03`", "`TASK-02`")

        with self.assertRaisesRegex(RESOLVER.PlanError, "missing from delivery groups"):
            RESOLVER.resolve_group(plan, "PG-01")


if __name__ == "__main__":
    unittest.main()
