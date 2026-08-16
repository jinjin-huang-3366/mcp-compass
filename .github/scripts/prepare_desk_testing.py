import os
from pathlib import Path


SERVICE_STARTUP = """### Start the application services

1. Start PostgreSQL with `docker compose up -d db`.
2. In a dedicated terminal, start the backend with `./mvnw -pl backend spring-boot:run` (macOS/Linux) or `.\\mvnw.cmd -pl backend spring-boot:run` (Windows PowerShell).
3. Wait for `http://localhost:8080/actuator/health` to report `UP`.
4. In a second terminal, run `cd web`, `npm install`, and `npm run dev`.
5. Wait for `http://localhost:3000` to load before performing the task-specific checks.
6. After testing, stop both development servers with Ctrl+C and run `docker compose down`.
"""


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def render_desk_testing(task_steps: str) -> str:
    steps = task_steps.strip()
    if not steps:
        raise ValueError("Task-specific desk testing must not be empty")
    return f"{SERVICE_STARTUP}\n### Task-specific checks\n\n{steps}\n"


def main() -> None:
    output_path = Path(required_environment("DESK_TESTING_PATH"))
    task_steps = required_environment("TASK_DESK_TESTING")
    output_path.write_text(render_desk_testing(task_steps), encoding="utf-8")


if __name__ == "__main__":
    main()
