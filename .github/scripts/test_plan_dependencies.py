import re
import unittest
from pathlib import Path


TASK_PATTERN = re.compile(
    r"^- \[(?P<status>[ x])\] \*\*(?P<id>[A-Z]+-\d{2})\*\* — .+ "
    r"_\(Depends on: (?P<dependencies>none|[A-Z]+-\d{2}(?:, [A-Z]+-\d{2})*)\)_$"
)


class PlanDependenciesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        plans_path = Path(__file__).resolve().parents[2] / "PLANS.md"
        cls.checkbox_lines = [
            line
            for line in plans_path.read_text(encoding="utf-8").splitlines()
            if line.startswith("- [")
        ]

    def parse_tasks(self) -> dict[str, tuple[bool, list[str]]]:
        tasks: dict[str, tuple[bool, list[str]]] = {}
        malformed: list[str] = []
        for line in self.checkbox_lines:
            match = TASK_PATTERN.fullmatch(line)
            if match is None:
                malformed.append(line)
                continue
            task_id = match.group("id")
            self.assertNotIn(task_id, tasks, f"Duplicate task ID: {task_id}")
            dependencies = match.group("dependencies")
            tasks[task_id] = (
                match.group("status") == "x",
                [] if dependencies == "none" else dependencies.split(", "),
            )
        self.assertFalse(malformed, f"Malformed task lines: {malformed}")
        return tasks

    def test_every_checkbox_has_a_unique_id_and_valid_dependencies(self) -> None:
        tasks = self.parse_tasks()
        self.assertEqual(len(self.checkbox_lines), len(tasks))
        for task_id, (completed, dependencies) in tasks.items():
            self.assertNotIn(task_id, dependencies, f"{task_id} depends on itself")
            for dependency in dependencies:
                self.assertIn(dependency, tasks, f"Unknown dependency for {task_id}")
                if completed:
                    self.assertTrue(
                        tasks[dependency][0],
                        f"Completed task {task_id} depends on incomplete {dependency}",
                    )

    def test_dependency_graph_is_acyclic(self) -> None:
        tasks = self.parse_tasks()
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(task_id: str) -> None:
            if task_id in visiting:
                self.fail(f"Dependency cycle includes {task_id}")
            if task_id in visited:
                return
            visiting.add(task_id)
            for dependency in tasks[task_id][1]:
                visit(dependency)
            visiting.remove(task_id)
            visited.add(task_id)

        for task_id in tasks:
            visit(task_id)


if __name__ == "__main__":
    unittest.main()
