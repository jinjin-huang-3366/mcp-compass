---
name: mcp-task-pr-flow
description: Implement and publish exactly one MCP Compass task using the active local Codex session, then dispatch and monitor .github/workflows/task-pr.yml for validation, pull request creation, and an email summary without merging. Use when the user invokes $mcp-task-pr-flow, asks to run or start the single-task PR flow, or wants a task implemented locally and sent through the repository's validation and PR workflow without OpenAI API usage in GitHub Actions.
---

# MCP task PR flow

Implement exactly one task with the active local Codex session, push its branch, and then run `.github/workflows/task-pr.yml`. Treat the workflow as the executable source of truth for remote validation, pull request creation, and email. Do not start a follow-up task or merge the pull request.

## Collect inputs

Require a concrete task description. Accept or derive these values:

- `branch_name`: default to a short `task/<slug>` name.
- `base_branch`: default to `main`.
- `pr_title`: derive a concise, behavior-focused title.

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
2. Implement only the requested task. Follow repository instructions and applicable skills, update documentation when required, and do not select another backlog item.
3. Run the narrowest relevant checks, followed by the repository checks appropriate to the change.
4. Review the diff for scope and secrets. Stage only the intended files.
5. Commit with the pull request title or another concise behavior-focused message and push `branch_name` without force.
6. Prepare a concise summary of the completed changes and validation results for the pull request and email.

If the environment cannot write the local Git index but an authenticated GitHub API or CLI can safely create the branch and commit the exact validated files, use that as a fallback. Never update the base branch or overwrite an existing task branch.

## Dispatch once

Dispatch the workflow once using the pushed task branch so the workflow definition and source both come from that branch:

```text
gh workflow run task-pr.yml --ref <branch-name> -f task=<task-description> -f branch_name=<branch-name> -f base_branch=<base-branch> -f pr_title=<pr-title> -f summary=<local-codex-summary>
```

Record the returned run URL or identify the newly created `workflow_dispatch` run using `gh run list`. Correlate it by workflow, actor, branch ref, and creation time. Never dispatch a second run merely because the first run is queued or slow.

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
2. Confirm its base is `base_branch`, its commit exists, and it is not merged or configured for automatic merge.
3. Confirm the workflow validation steps and `Email pull request summary` step succeeded. Do not expose SMTP or repository secrets.
4. Report the run URL, branch, commit, pull request URL, validation results, and email-step result.
5. Stop. Never merge the pull request or start a follow-up task without a new explicit request.

Do not claim the flow succeeded when validation was not completed, the pull request was not created, or the email step did not succeed.
