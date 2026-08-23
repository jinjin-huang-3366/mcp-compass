import argparse
import os
import re
from pathlib import Path


MARKER_PREFIX = "<!-- mcp-compass-plan-item:"
MARKER_PATTERN = re.compile(r"<!-- mcp-compass-plan-item: ([^\r\n]+?) -->")
TASK_PATTERN = re.compile(
    r"^- \[(?P<state>[ xX])\] \*\*(?P<task_id>[A-Z]+-\d+)\*\* "
)
GROUP_PATTERN = re.compile(
    r"^(?P<prefix>\| \*\*(?P<group_id>PG-\d+).+?\*\* "
    r"\| (?P<members>[^|]+) \| (?P<start_after>[^|]+) \| )"
    r"(?P<status>[^|]+) \|(?P<newline>\r?\n)?$"
)
TASK_ID_PATTERN = re.compile(r"`([A-Z]+-\d+)`")


class PlanItemError(ValueError):
    pass


def validate_plan_item(item: str) -> str:
    if (
        not item
        or len(item) > 300
        or item != item.strip()
        or "\n" in item
        or "\r" in item
        or "-->" in item
    ):
        raise PlanItemError(
            "Plan item must be 1-300 trimmed characters without newlines or '-->'."
        )
    return item


def extract_plan_item(pr_body: str) -> str | None:
    matches = MARKER_PATTERN.findall(pr_body)
    marker_count = pr_body.count(MARKER_PREFIX)
    if not matches:
        if marker_count:
            raise PlanItemError("Malformed MCP Compass plan-item marker.")
        return None
    if marker_count != len(matches):
        raise PlanItemError("Malformed MCP Compass plan-item marker.")
    if len(matches) != 1:
        raise PlanItemError("Expected exactly one MCP Compass plan-item marker.")

    return validate_plan_item(matches[0])


def mark_plan_item_complete(plans: str, item: str) -> tuple[str, bool]:
    item = validate_plan_item(item)
    unchecked = f"- [ ] {item}"
    checked = f"- [x] {item}"
    lines = plans.splitlines(keepends=True)
    unchecked_indexes = [
        index for index, line in enumerate(lines) if line.rstrip("\r\n") == unchecked
    ]
    checked_indexes = [
        index for index, line in enumerate(lines) if line.rstrip("\r\n") == checked
    ]

    if checked_indexes and not unchecked_indexes:
        return plans, False
    if len(unchecked_indexes) != 1 or checked_indexes:
        raise PlanItemError(f"Expected one exact unchecked PLANS.md item: {item}")

    index = unchecked_indexes[0]
    newline = lines[index][len(lines[index].rstrip("\r\n")) :]
    lines[index] = checked + newline
    return "".join(lines), True


def format_group_status(completed: int, total: int) -> str:
    if completed == total:
        return f"Complete ({completed}/{total})"
    if completed == 0:
        return f"Not started ({completed}/{total})"
    return f"In progress ({completed}/{total})"


def update_parallel_group_statuses(plans: str) -> tuple[str, bool]:
    """Derive every delivery-group status from its canonical task checkboxes."""
    task_states: dict[str, bool] = {}
    for line in plans.splitlines():
        match = TASK_PATTERN.match(line)
        if match:
            task_id = match.group("task_id")
            if task_id in task_states:
                raise PlanItemError(f"Duplicate canonical PLANS.md task: {task_id}")
            task_states[task_id] = match.group("state").lower() == "x"

    changed = False
    updated_lines: list[str] = []
    for line in plans.splitlines(keepends=True):
        match = GROUP_PATTERN.match(line)
        if not match:
            updated_lines.append(line)
            continue

        members = TASK_ID_PATTERN.findall(match.group("members"))
        if not members:
            raise PlanItemError(
                f"Parallel delivery group has no task IDs: {match.group('group_id')}"
            )
        unknown = [task_id for task_id in members if task_id not in task_states]
        if unknown:
            raise PlanItemError(
                f"{match.group('group_id')} references unknown task(s): "
                + ", ".join(unknown)
            )

        completed = sum(task_states[task_id] for task_id in members)
        expected = format_group_status(completed, len(members))
        newline = match.group("newline") or ""
        updated_line = f"{match.group('prefix')}{expected} |{newline}"
        updated_lines.append(updated_line)
        changed = changed or updated_line != line

    return "".join(updated_lines), changed


def write_output(name: str, value: str) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with Path(output_path).open("a", encoding="utf-8") as output:
            output.write(f"{name}={value}\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-item")
    args = parser.parse_args()

    plans_path = Path("PLANS.md")
    plans = plans_path.read_text(encoding="utf-8")
    item = args.check_item
    if item is None:
        item = extract_plan_item(os.environ.get("PR_BODY", ""))

    if item is None:
        write_output("changed", "false")
        print("No linked MCP Compass plan item; nothing to update.")
        return

    updated_plans, item_changed = mark_plan_item_complete(plans, item)
    if args.check_item is not None:
        print(f"Validated unchecked MCP Compass plan item: {item}")
        return

    updated_plans, group_changed = update_parallel_group_statuses(updated_plans)
    changed = item_changed or group_changed
    if changed:
        plans_path.write_text(updated_plans, encoding="utf-8")
    write_output("changed", str(changed).lower())
    write_output("plan_item", item)
    print(
        f"Plan item {'marked complete' if item_changed else 'already complete'}: {item}"
    )
    if group_changed:
        print("Parallel delivery group completion status updated.")


if __name__ == "__main__":
    main()
