import argparse
import os
import re
from pathlib import Path


MARKER_PREFIX = "<!-- mcp-compass-plan-item:"
MARKER_PATTERN = re.compile(r"<!-- mcp-compass-plan-item: ([^\r\n]+?) -->")


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

    updated_plans, changed = mark_plan_item_complete(plans, item)
    if args.check_item is not None:
        print(f"Validated unchecked MCP Compass plan item: {item}")
        return

    if changed:
        plans_path.write_text(updated_plans, encoding="utf-8")
    write_output("changed", str(changed).lower())
    write_output("plan_item", item)
    print(f"Plan item {'marked complete' if changed else 'already complete'}: {item}")


if __name__ == "__main__":
    main()
