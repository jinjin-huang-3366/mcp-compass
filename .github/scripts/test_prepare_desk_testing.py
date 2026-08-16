import unittest

from prepare_desk_testing import render_desk_testing


class RenderDeskTestingTest(unittest.TestCase):
    def test_prepends_service_startup_and_preserves_task_steps(self) -> None:
        task_steps = """1. Submit a search requirement.
2. Inspect the top three matches.

Expected: each match explains its score."""

        rendered = render_desk_testing(task_steps)

        self.assertIn("docker compose up -d db", rendered)
        self.assertIn("./mvnw -pl backend spring-boot:run", rendered)
        self.assertIn(".\\mvnw.cmd -pl backend spring-boot:run", rendered)
        self.assertIn("http://localhost:8080/actuator/health", rendered)
        self.assertIn("npm run dev", rendered)
        self.assertIn("http://localhost:3000", rendered)
        self.assertIn("docker compose down", rendered)
        self.assertTrue(rendered.endswith(task_steps + "\n"))

    def test_rejects_empty_task_steps(self) -> None:
        with self.assertRaises(ValueError):
            render_desk_testing("  \n")


if __name__ == "__main__":
    unittest.main()
