---
name: mcp-task-batch-flow
description: Deliver one MCP Compass PLANS.md parallel group as multiple independent, conflict-checked task branches and pull requests by orchestrating isolated subagents that each follow mcp-task-pr-flow. Use when the user invokes $mcp-task-batch-flow, asks to execute a PG-* group, or wants one command to fan out dependency-independent plan tasks into separate validated PRs and email summaries without merging them.
---

# MCP task batch flow

Turn one canonical `PG-*` group into separate task-flow runs. Keep `$mcp-task-pr-flow` as the execution primitive: one task, branch, workflow dispatch, plan marker, email, and pull request per child agent.

This skill requires subagent support. Never implement multiple group tasks in the root agent or combine them into one branch or PR.

## Resolve the batch

1. Require exactly one group ID such as `PG-01`; reject vague requests such as "the next group" when more than one group could qualify.
2. Read `AGENTS.md`, `PLANS.md`, `.agents/skills/mcp-task-pr-flow/SKILL.md`, `.github/workflows/task-pr.yml`, and the automated task-PR section of `README.md`.
3. Resolve the group from a fresh view of the requested base branch, defaulting to `main`:

   ```text
   python .agents/skills/mcp-task-batch-flow/scripts/resolve_group.py --plans PLANS.md --group PG-01 --require-ready --pretty
   ```

4. Treat the resolver's `plan_item` as the exact task-flow input. It intentionally excludes the `- [ ]` prefix.
5. Treat the resolver's `completion_status` as the table's canonical progress summary. The resolver rejects a status that does not match the member checkboxes.
6. Skip group members already checked. If all members are checked, report a no-op and stop without creating branches or dispatching workflows.
7. If any unchecked member is blocked, missing, duplicated, or dependent on another member of the same group, stop the entire batch before mutation.

## Preflight the whole batch

Complete these checks before starting any child:

1. Confirm the intended repository, base branch, base commit, and `task-pr.yml` on that base.
2. Confirm GitHub authentication and permissions plus the existence of `GMAIL_ADDRESS` and `GMAIL_APP_PASSWORD` without reading their values.
3. Assign deterministic branches `task/<lowercase-task-id>`, for example `task/srch-04`.
4. Validate every branch with `git check-ref-format --branch` and confirm no assigned branch or open PR already exists locally or remotely. Never overwrite or force-push.
5. Create one isolated clone or worktree per actionable task from the same base commit. Never let children share a working tree or inherit another task's changes. The pinned commit is the common starting point, not permission to publish a stale branch: every child must synchronize with the latest base through `$mcp-task-pr-flow` before dispatch.
6. Present the resolved task list, skipped completed tasks, base commit, branches, and concurrency limit in the working plan.

Treat any failed preflight as batch-blocking. Do not create a partial set of branches when the failure is discoverable in advance.

## Fan out task agents

1. Reserve the root agent for coordination. Use at most the remaining available agent slots; queue excess tasks locally.
2. Spawn one child agent per active task with only the task-local context it needs:
   - isolated checkout path and pinned base commit;
   - task ID, canonical task text, and deterministic branch;
   - explicit instruction to use `$mcp-task-pr-flow` for exactly that task;
   - instruction to include one concrete before/after, request/response, or user-visible example in the pull request and email;
   - instruction to return branch, commit, task workflow URL, PR URL, baseline CI result, email result, and any failure.
3. Require each child to read the applicable repository skills and documents itself. Do not pre-decide task implementation details in the coordinator.
4. As a child reaches a terminal state, launch the next queued task until the group is exhausted or a child fails.
5. Expect `.github/workflows/task-pr.yml` runs to queue because the repository uses the `codex-single-task-flow` concurrency group. A queued run is not a reason to redispatch it.
6. Before accepting a child's result, require its final pull request head to contain the latest base, be reported `MERGEABLE` by GitHub, and have passing baseline CI on that exact head. If the base advanced, return the task to that child for the single post-creation synchronization allowed by `$mcp-task-pr-flow`.

Each child must leave its canonical checkbox unchecked in its task branch. The merge workflow completes that one exact item only after manual merge.

The `Status` column in the parallel delivery table is derived from those canonical checkboxes. Each merged task PR updates the group count, and the merge workflow changes the group to `Complete (n/n)` only when every listed task PR has been merged. The batch flow must not edit this status itself or report the group complete while any child PR remains open.

## Failure policy

When any child fails:

1. Let that child apply the single-task flow's bounded retry policy: at most two retries after its initial attempt, with diagnosis and an in-scope correction or confirmed transient cause before each retry. The coordinator must not spawn a duplicate child or independently redispatch the same branch.
2. While a child is retrying, do not launch new queued tasks. Let already-running siblings reach a safe terminal state; do not interrupt a publish or workflow operation solely because of the retry.
3. If the child succeeds within three total attempts, continue the queue. If it reaches a terminal failure, stop launching tasks and do not merge, force-push, or make coordinator-side compensating changes.
4. Report successful PRs, terminally failed tasks, untouched queued tasks, every attempt URL/outcome, and the first causal error for each terminal failure.

The user must explicitly request a resumed batch. On resume, rerun the full preflight against current `main`, skip tasks now checked, and never overwrite branches left by the earlier attempt.

## Verify and report

For every successful child, independently verify:

- branch and commit match the task PR;
- base branch is correct;
- PR is open, unmerged, and has no auto-merge;
- the latest base commit is an ancestor of the final PR head and GitHub reports it `MERGEABLE`;
- PR body contains complete desk testing and the exact hidden plan marker;
- PR body contains the task's concrete example;
- task workflow validation, final-head baseline CI, and email step succeeded.

Return a compact table with one row per group member: task, status, branch, PR, workflow, and CI. Include the group's current completion status, list completed tasks skipped by the resolver and tasks not started after a failure, and note that the merge workflow will advance the count after each manual merge. Stop without merging or starting a later group.

The checks guarantee that every PR is conflict-free against the base at batch handoff. Because the PRs remain independent and unmerged, a later base update—including merging a sibling PR—can invalidate that state; re-run the single-task synchronization for any affected PR immediately before its manual merge.

## Example

```text
Use $mcp-task-batch-flow for PG-01.
```

This means "create separate task PRs for every currently unchecked, ready member of `PG-01`"; it never means one combined PG-01 PR.
