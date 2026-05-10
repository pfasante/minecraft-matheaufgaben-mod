# CLAUDE.md (bootstrap)

This is a *pre-execution* CLAUDE.md — a small orientation note for a fresh Claude Code session that picks up this project to execute the implementation plan.
Task 13 of the plan replaces this file with an architecture-focused permanent version.

## Project

A Fabric client-side mod for Minecraft Java Edition 1.21.x: interrupts singleplayer gameplay every X minutes with a math-problem prompt that the kid must solve to resume play.

The mod ships four problem types — addition (`plus`), subtraction (`minus`), multiplication tables (`einmaleins`), and division (`division`) — each parameterised through a `type:k=v[,k=v...]` section spec entry in the JSON config.

## What to do first

1. **Read the plan, not the spec.**
   The plan (`docs/superpowers/plans/2026-05-10-minecraft-matheaufgaben-mod.md`) has every file, every code block, every command, every commit message — it is fully self-contained.
   The spec (`docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`) is the design rationale — useful if you encounter a judgement call the plan didn't anticipate, otherwise the plan supersedes it.

2. **Create a feature branch before Task 1.**
   ```sh
   git checkout -b feat/mvp
   ```
   The plan's commits land there, then merge back to `main` at the end via `superpowers:finishing-a-development-branch`.

3. **Execution mode: subagent-driven.**
   Invoke `superpowers:subagent-driven-development`, dispatch one implementer subagent per task, two-stage review (spec compliance, then code quality) between tasks.

## Resuming a partially-completed plan

If `git log feat/mvp` already shows some plan commits, the project is mid-execution.
Determine which task to resume from by comparing the latest commit message to the plan's task titles, then dispatch the next task's implementer.
Don't re-run completed tasks — the implementer subagents are not idempotent against a non-empty tree.

## What is *not* in scope

See the spec's "Non-goals (v1)" section.
TL;DR: singleplayer-only, client-only, no in-game GUI, no multi-version support, no progress tracking, no skip buttons, German-only UI strings.

If a subagent asks "should I add X" and X isn't named in the plan, the answer is almost certainly "no" — the spec deliberately scoped this small.
