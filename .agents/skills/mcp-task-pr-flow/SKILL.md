---
name: mcp-task-pr-flow
description: Implement and publish exactly one MCP Compass task using the active local Codex session, synchronize it with the latest base, then dispatch and monitor .github/workflows/task-pr.yml for validation, conflict-free pull request creation, an email summary, and linked PLANS.md completion after a later manual merge. Use when the user invokes $mcp-task-pr-flow, asks to run or start the single-task PR flow, or wants a task implemented locally and sent through the repository's validation and PR workflow without OpenAI API usage in GitHub Actions.
---

# MCP task PR flow

Implement exactly one task with the active local Codex session, push its branch, and then run `.github/workflows/task-pr.yml`. Treat the workflow as the executable source of truth for remote validation, pull request creation, and email. Do not start a follow-up task or merge the pull request.

## Collect inputs

Require a concrete task description. Accept or derive these values:

- `branch_name`: default to a short `task/<slug>` name.
- `base_branch`: default to `main`.
- `pr_title`: derive a concise, behavior-focused title.
- `desk_testing`: provide complete, reproducible task-specific checks with ordered commands or actions, expected results, and any unperformed steps with the reason. The workflow automatically prepends standard PostgreSQL, backend, and frontend startup, readiness, and cleanup steps.
- `plan_item`: use the exact text of one unchecked `PLANS.md` entry when the task implements it; otherwise use an empty value.

Make reasonable naming assumptions, but stop for user input when the task itself is ambiguous or the branch choice could overwrite or collide with existing work.

## Preflight

1. Read `/AGENTS.md`, `PLANS.md`, the relevant documents and repository skills, `.github/workflows/task-pr.yml`, and the automated task-PR section of `README.md`.
2. Confirm the repository remote is the intended MCP Compass repository and the workflow exists on `base_branch`.
3. Confirm GitHub CLI authentication and permission to push a branch and dispatch Actions workflows. Prefer an available GitHub connector when it supports the required operation.
4. Verify that repository secrets named `GMAIL_ADDRESS` and `GMAIL_APP_PASSWORD` exist without reading or printing their values. No OpenAI credential is required by this flow.
5. Require a clean understanding of the local working tree. Never include unrelated local changes.
6. Validate `branch_name` with `git check-ref-format --branch` and confirm the branch does not already exist locally or remotely. Never overwrite an existing branch.
7. Note that the workflow sends mail to the address stored in `GMAIL_ADDRESS`. Do not claim that it is a particular address because secret values cannot be inspected.

Treat authentication, permissions, missing email secrets, an existing branch, an unavailable workflow, or unrelated changes that cannot be isolated as intervention-required errors.

## Implement locally

1. Update the local `base_branch` reference when permitted and create `branch_name` from it.
2. Implement only the requested task. Follow repository instructions and applicable skills, update documentation when required, and do not select another backlog item. When `plan_item` is set, leave its checkbox unchecked in the task branch so the merge workflow can mark it complete after the PR is merged.
3. Immediately before final validation, fetch the latest remote `base_branch`. If it is not an ancestor of the task head, merge it into the task branch, resolve conflicts while preserving both intended behaviors, and review the combined diff. Do not rebase or force-push an already published task branch.
4. Run the narrowest relevant checks, followed by the repository checks appropriate to the combined change. Perform relevant desk testing when the environment permits; otherwise record each unperformed step and the reason. The generated handoff always includes standard PostgreSQL, backend, and frontend startup, readiness, and cleanup steps before the task-specific checks.
5. Review the diff for scope and secrets. Stage only the intended files.
6. Commit with the pull request title or another concise behavior-focused message and push `branch_name` without force.
7. Fetch `base_branch` again immediately before dispatch and require it to be an ancestor of the pushed task head. If the base advanced, repeat the synchronization, review, validation, commit, and push cycle before dispatching.
8. Prepare a concise summary of the completed changes and validation results, plus complete `desk_testing` guidance, for the pull request and email.

If the environment cannot write the local Git index but an authenticated GitHub API or CLI can safely create the branch and commit the exact validated files, use that as a fallback. Never update the base branch or overwrite an existing task branch.

## Dispatch once

Dispatch the workflow once using the pushed task branch so the workflow definition and source both come from that branch:

```text
gh workflow run task-pr.yml --ref <branch-name> -f task=<task-description> -f branch_name=<branch-name> -f base_branch=<base-branch> -f pr_title=<pr-title> -f summary=<local-codex-summary> -f desk_testing=<complete-desk-testing-guidance> -f plan_item=<exact-plan-item-or-empty>
```

Record the returned run URL or identify the newly created `workflow_dispatch` run using `gh run list`. Correlate it by workflow, actor, branch ref, and creation time. Never dispatch a second run merely because the first run is queued or slow.

The workflow checks twice that the task head contains the latest fetched base: once before validation and again immediately before pull request creation. A stale branch fails before a pull request is opened.

## Monitor

Poll the run with `gh run view <run-id> --json status,conclusion,url,jobs`. Provide a concise user update at least once per minute while it is active; avoid one long blocking watch command.

On failure:

1. Collect read-only diagnostics with `gh run view <run-id> --log-failed`.
2. Determine whether validation, pull request creation, or email completed. The local branch and commit already exist before dispatch.
3. Stop without retrying, merging, starting another task, or making compensating repository changes.
4. Report the first causal error and the intervention required.

The repository workflow sends email only after successful pull request creation. Do not retry the workflow automatically when validation or email fails.

## Verify completion

After a successful workflow run:

1. Find the open pull request whose head is `branch_name`.
2. Confirm its base is `base_branch`, its commit exists, and it is not merged or configured for automatic merge. Confirm that its `Desk testing` section contains the standard service startup/readiness/cleanup block followed by the complete task-specific guidance supplied at dispatch. When `plan_item` is set, also confirm the pull request body contains the exact hidden plan-item marker.
3. Wait for GitHub to calculate mergeability and require `MERGEABLE`, never `CONFLICTING` or an unresolved `UNKNOWN`. Fetch the base once more and confirm its current head is an ancestor of the pull request head.
4. If the base advanced after pull request creation, perform one post-creation synchronization: merge the latest base locally, resolve conflicts, rerun the relevant validation, commit, and push without force. Do not redispatch `task-pr.yml`; wait for baseline CI on the new pull request head. If the base advances again or resolution would change task scope, stop and report intervention required.
5. Confirm the workflow validation steps and `Email pull request summary` step succeeded, and confirm baseline CI succeeded for the final pull request head. Do not expose SMTP or repository secrets.
6. Report the run URL, branch, final commit, pull request URL, mergeability, validation results, desk-testing handoff, and email-step result.
7. Stop. Never merge the pull request or start a follow-up task without a new explicit request.

Do not claim the flow succeeded when validation was not completed, the service startup or task-specific desk-testing guidance is missing or incomplete, the pull request was not created, the final head is not conflict-free against the current base, final-head CI is incomplete, or the email step did not succeed. This is a handoff-time guarantee; a future base-branch update can require another explicit synchronization before merge.

After a linked pull request is merged manually, `.github/workflows/plan-completion.yml` marks its exact `PLANS.md` item complete. Pull requests without a valid marker are ignored.
