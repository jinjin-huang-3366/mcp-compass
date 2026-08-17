#!/usr/bin/env python3
"""Resolve and validate one MCP Compass parallel delivery group."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


TASK_PATTERN = re.compile(
    r"^- \[(?P<state>[ xX])\] "
    r"(?P<item>\*\*(?P<task_id>[A-Z]+-\d+)\*\* .+? "
    r"_\(Depends on: (?P<dependencies>[^)]+)\)_)\s*$"
)
GROUP_PATTERN = re.compile(
    r"^\| \*\*(?P<group_id>PG-\d+)(?:\s+—\s+(?P<title>[^*]+))?\*\* "
    r"\| (?P<members>[^|]+) \| (?P<start_after>[^|]+) \|$"
)
TASK_ID_PATTERN = re.compile(r"`([A-Z]+-\d+)`")


class PlanError(ValueError):
    """Raised when PLANS.md cannot safely resolve a batch."""


@dataclass(frozen=True)
class Task:
    task_id: str
    checked: bool
    plan_item: str
    description: str
    dependencies: tuple[str, ...]


@dataclass(frozen=True)
class Group:
    group_id: str
    title: str
    members: tuple[str, ...]
    start_after: str


def parse_plan(text: str) -> tuple[dict[str, Task], dict[str, Group]]:
    tasks: dict[str, Task] = {}
    groups: dict[str, Group] = {}

    for line in text.splitlines():
        task_match = TASK_PATTERN.match(line)
        if task_match:
            task_id = task_match.group("task_id")
            if task_id in tasks:
                raise PlanError(f"Duplicate canonical task: {task_id}")
            dependency_text = task_match.group("dependencies")
            dependencies = (
                ()
                if dependency_text == "none"
                else tuple(value.strip() for value in dependency_text.split(","))
            )
            item = task_match.group("item")
            description = re.sub(
                rf"^\*\*{re.escape(task_id)}\*\*\s+—\s+",
                "",
                item,
            )
            description = re.sub(r"\s+_\(Depends on: [^)]+\)_$", "", description)
            tasks[task_id] = Task(
                task_id=task_id,
                checked=task_match.group("state").lower() == "x",
                plan_item=item,
                description=description,
                dependencies=dependencies,
            )
            continue

        group_match = GROUP_PATTERN.match(line)
        if group_match:
            group_id = group_match.group("group_id")
            if group_id in groups:
                raise PlanError(f"Duplicate delivery group: {group_id}")
            members = tuple(TASK_ID_PATTERN.findall(group_match.group("members")))
            if not members:
                raise PlanError(f"Delivery group has no task IDs: {group_id}")
            groups[group_id] = Group(
                group_id=group_id,
                title=(group_match.group("title") or "").strip(),
                members=members,
                start_after=group_match.group("start_after").strip(),
            )

    if not tasks:
        raise PlanError("No canonical tasks found in PLANS.md")
    if not groups:
        raise PlanError("No parallel delivery groups found in PLANS.md")
    validate_plan(tasks, groups)
    return tasks, groups


def validate_plan(tasks: dict[str, Task], groups: dict[str, Group]) -> None:
    for task in tasks.values():
        unknown = set(task.dependencies) - tasks.keys()
        if unknown:
            raise PlanError(
                f"{task.task_id} has unknown dependencies: {', '.join(sorted(unknown))}"
            )

    membership: dict[str, str] = {}
    for group in groups.values():
        for task_id in group.members:
            if task_id not in tasks:
                raise PlanError(f"{group.group_id} references unknown task: {task_id}")
            if task_id in membership:
                raise PlanError(
                    f"Task {task_id} appears in both {membership[task_id]} and {group.group_id}"
                )
            membership[task_id] = group.group_id

        members = set(group.members)
        for task_id in group.members:
            same_group = ancestors(task_id, tasks) & members
            if same_group:
                raise PlanError(
                    f"{task_id} depends on same-group task(s): "
                    f"{', '.join(sorted(same_group))}"
                )

    missing_unchecked = {
        task_id for task_id, task in tasks.items() if not task.checked
    } - membership.keys()
    if missing_unchecked:
        raise PlanError(
            "Unchecked tasks missing from delivery groups: "
            + ", ".join(sorted(missing_unchecked))
        )


def ancestors(
    task_id: str,
    tasks: dict[str, Task],
    seen: set[str] | None = None,
) -> set[str]:
    result = set() if seen is None else seen
    for dependency in tasks[task_id].dependencies:
        if dependency not in result:
            result.add(dependency)
            ancestors(dependency, tasks, result)
    return result


def resolve_group(text: str, requested_group: str) -> dict[str, object]:
    tasks, groups = parse_plan(text)
    group_id = requested_group.upper()
    if group_id not in groups:
        available = ", ".join(sorted(groups))
        raise PlanError(f"Unknown delivery group {group_id}; available groups: {available}")

    group = groups[group_id]
    resolved_tasks: list[dict[str, object]] = []
    ready_ids: list[str] = []
    completed_ids: list[str] = []
    blocked_ids: list[str] = []

    for task_id in group.members:
        task = tasks[task_id]
        blocked_by = [
            dependency
            for dependency in task.dependencies
            if not tasks[dependency].checked
        ]
        if task.checked:
            status = "completed"
            completed_ids.append(task_id)
        elif blocked_by:
            status = "blocked"
            blocked_ids.append(task_id)
        else:
            status = "ready"
            ready_ids.append(task_id)

        resolved_tasks.append(
            {
                "id": task_id,
                "status": status,
                "branch_name": f"task/{task_id.lower()}",
                "description": task.description,
                "plan_item": task.plan_item,
                "dependencies": list(task.dependencies),
                "blocked_by": blocked_by,
            }
        )

    if blocked_ids:
        batch_status = "blocked"
    elif ready_ids:
        batch_status = "ready"
    else:
        batch_status = "completed"

    return {
        "group_id": group.group_id,
        "title": group.title,
        "start_after": group.start_after,
        "status": batch_status,
        "tasks": resolved_tasks,
        "ready_task_ids": ready_ids,
        "completed_task_ids": completed_ids,
        "blocked_task_ids": blocked_ids,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Resolve a PLANS.md PG-* group into exact single-task flow inputs."
    )
    parser.add_argument("--plans", type=Path, required=True, help="Path to PLANS.md")
    parser.add_argument("--group", required=True, help="Delivery group ID, for example PG-01")
    parser.add_argument(
        "--require-ready",
        action="store_true",
        help="Exit nonzero when the group is blocked or already complete",
    )
    parser.add_argument("--pretty", action="store_true", help="Pretty-print JSON")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = resolve_group(args.plans.read_text(encoding="utf-8"), args.group)
    except (OSError, PlanError) as error:
        print(json.dumps({"status": "error", "error": str(error)}), file=sys.stderr)
        return 2

    print(json.dumps(result, indent=2 if args.pretty else None, ensure_ascii=False))
    if args.require_ready and result["status"] != "ready":
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
