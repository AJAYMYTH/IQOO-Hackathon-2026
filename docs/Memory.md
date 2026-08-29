# Memory.md — Project Memory Log
## Purpose

This file is the single source of truth for "what's actually built and decided so far." Its job is to let anyone (a teammate or an AI coding agent) get up to speed in 30 seconds instead of re-reading the whole PRD/TRD or re-explaining context every time. Keep it short, current, and high-signal — this file is meant to be pasted into an AI agent's context regularly, so token efficiency matters.

**Update this after every meaningful milestone, not after every commit.** If you're updating it more than once an hour, you're logging too much detail — prune to decisions and state, not narration.

---

## How To Use This With an AI Coding Agent

Start every new AI agent session for this project with:
> "Read Memory.md first, then help me with [task]."

This means the agent gets current project state without you re-explaining the architecture, what's already built, or what decisions have already been made — saving both your time and token usage on repeated context.

---

## Format Rules

- One line per fact, tagged with a short date/phase marker
- **Status** section always reflects current reality — overwrite stale entries, don't just append forever
- **Decisions** section is append-only (a decision log, useful to never re-litigate the same debate)
- **Known Issues** section gets pruned the moment something is fixed — don't let it become a graveyard
- Keep the whole file under ~150 lines. If it's growing past that, archive old "Decisions" entries into a `Memory-Archive.md` and keep only what's still relevant to current work

---

## Template (fill in as you build)

### Current Status
*(Overwrite this section each update — it should always describe the state right now, not history)*
- Auth flow: [not started / in progress / working]
- Repo picker: ...
- On-device inference (MLCEngine wired to Review Screen): ...
- GitHub PR creation: ...
- Self-hosted runner validation loop: ...
- Voice trigger: ...
- CI/CD generator: ...

### Decisions Log
*(Append-only — one line per decision, with the reason, so it's never re-debated)*
- [date/phase] Chose Qwen2.5-Coder-3B over 7B — reason: inference speed on real device
- [date/phase] Chose MLC-LLM over llama.cpp — reason: ...

### Known Issues
*(Prune the moment something is fixed)*
- [ ] Example: voice trigger sometimes fails to recognize phrase in noisy venue — needs testing at actual venue noise level

### Next Up
*(Short list, not a full backlog — just what's next)*
- ...

---

## Example Entry (for reference — delete once real entries exist)

### Current Status — as of Red Light hour 6
- Auth flow: working (device flow tested against demo repo)
- Repo picker: working
- On-device inference: MLCEngine loads model, first-token latency ~2.1s on real device — acceptable
- GitHub PR creation: Member B has the API calls working as standalone Kotlin functions, not yet wired to UI
- Everything else: not started

### Decisions Log
- Red Light hour 2: Dropped 7B model in favor of 3B — 7B first-token latency was 8s+ on real hardware, too slow for a live demo
