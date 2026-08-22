#!/usr/bin/env python3
"""Require a task head to contain the latest fetched base commit."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def git(repo: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        cwd=repo,
        check=False,
        capture_output=True,
        text=True,
    )


def resolve_commit(repo: Path, reference: str) -> str:
    result = git(repo, "rev-parse", "--verify", f"{reference}^{{commit}}")
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise ValueError(f"Cannot resolve Git commit {reference!r}: {detail}")
    return result.stdout.strip()


def require_current_base(repo: Path, base_ref: str, head_ref: str) -> bool:
    base_commit = resolve_commit(repo, base_ref)
    head_commit = resolve_commit(repo, head_ref)
    result = git(repo, "merge-base", "--is-ancestor", base_commit, head_commit)
    if result.returncode == 0:
        print(f"Task head {head_commit} contains latest base {base_commit}.")
        return True
    if result.returncode == 1:
        print(
            f"Task head {head_commit} does not contain latest base {base_commit}. "
            "Synchronize the task branch with the base, resolve conflicts, rerun validation, "
            "and dispatch a new task flow only after the updated branch is pushed.",
            file=sys.stderr,
        )
        return False
    detail = result.stderr.strip() or result.stdout.strip()
    raise RuntimeError(f"git merge-base failed: {detail}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Fail unless the task head contains the latest fetched base commit."
    )
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    args = parser.parse_args(argv)

    try:
        return 0 if require_current_base(args.repo, args.base_ref, args.head_ref) else 1
    except (RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
