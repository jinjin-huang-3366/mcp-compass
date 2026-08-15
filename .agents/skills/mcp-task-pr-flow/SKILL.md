---
name: mcp-task-pr-flow
description: Dispatch and monitor the repository's manually triggered Codex task pull request workflow for exactly one MCP Compass task. Use when the user invokes $mcp-task-pr-flow, asks to run or start the single-task PR flow, or wants task instructions sent through .github/workflows/task-pr.yml with validation, branch and commit creation, a pull request, and an email summary without merging.
---

# MCP task PR flow

Run exactly one task through `.github/workflows/task-pr.yml`. Treat that workflow as the executable source of truth; do not implement the task locally or duplicate its branch, commit, push, PR, or email steps.

## Collect inputs

Require a concrete task description. Accept or derive these values:

- `branch_name`: default to a short `task/<slug>` name.
- `base_branch`: default to `main`.
- `pr_title`: derive a concise, behavior-focused title.

Make reasonable naming assumptions, but stop for user input when the task itself is ambiguous or the branch choice could overwrite or collide with existing work.

## Preflight

1. Read `/AGENTS.md`, `PLANS.md`, `.github/workflows/task-pr.yml`, and the automated task-PR section of `README.md`.
2. Confirm the repository remote is the intended MCP Compass repository and the workflow exists on the selected base branch.
3. Confirm GitHub CLI authentication and permission to dispatch Actions workflows. Prefer an available GitHub connector only when it supports the same dispatch and monitoring operations.
4. Verify that repository secrets named `OPENAI_API_KEY`, `GMAIL_ADDRESS`, and `GMAIL_APP_PASSWORD` exist without reading or printing their values.
5. Validate `branch_name` with `git check-ref-format --branch` and confirm the remote branch does not already exist. Never overwrite an existing branch.
6. Note that the current workflow sends mail to the address stored in `GMAIL_ADDRESS`. Do not claim that it is a particular address because secret values cannot be inspected.
7. Do not include unrelated local working-tree changes; the remote workflow starts from `base_branch`.

Treat authentication, permissions, missing secrets, an existing branch, or an unavailable workflow as intervention-required errors. Stop before dispatch and report the exact blocker.

## Dispatch once

Dispatch the workflow once with the collected inputs:

```text
gh workflow run task-pr.yml --ref <base-branch> -f task=<task-description> -f branch_name=<branch-name> -f base_branch=<base-branch> -f pr_title=<pr-title>
```

Record the returned run URL or identify the newly created `workflow_dispatch` run using `gh run list`. Correlate it by workflow, actor, base ref, and creation time. Never dispatch a second run merely because the first run is queued or slow.

## Monitor

Poll the run with `gh run view <run-id> --json status,conclusion,url,jobs`. Provide a concise user update at least once per minute while it is active; avoid one long blocking watch command.

On failure:

1. Collect read-only diagnostics with `gh run view <run-id> --log-failed`.
2. Determine which stage completed, including whether a branch, commit, PR, or email may already exist.
3. Stop without retrying, merging, starting another task, or making compensating repository changes.
4. Report the first causal error and the intervention required.

Treat API quota or rate-limit errors as intervention-required failures. An exact 99.9% quota threshold is enforceable only when the runtime exposes a reliable percentage metric; never estimate it from context or token counts. If such a metric is available and reaches 99.9%, cancel the active run, stop, and use an authorized email capability to send the current status. If no metric or email capability is available, state that limitation explicitly. The repository workflow itself currently sends email only after successful PR creation.

## Verify completion

After a successful workflow run:

1. Find the open PR whose head is `branch_name`.
2. Confirm its base is `base_branch`, its commit exists, and it is not merged or configured for automatic merge.
3. Confirm the workflow's `Email pull request summary` step succeeded. Do not expose SMTP or repository secrets.
4. Report the run URL, branch, commit, PR URL, validation results, and email-step result.
5. Stop. Never merge the PR or start a follow-up task without a new explicit request.

Do not claim the flow succeeded when it was only dispatched, when validation was not completed, or when email delivery was not attempted successfully.
