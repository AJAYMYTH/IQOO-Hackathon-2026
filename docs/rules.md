# rules.md — Project Rules for AI Agents
## Purpose

This file defines how any AI coding agent (Claude Code, Replit AI, or the on-device model itself when reviewing commits) should behave on this project. It's structured in categorized sections rather than a flat list, so it stays readable and each team member can add their own preferences without turning it into clutter. Keep entries short and directive — this gets fed into agent context repeatedly, so verbosity costs tokens and dilutes signal.

---

## 1. Project Context
*(Facts the agent should never have to be re-told)*
- This is a native Android (Kotlin) app using MLC-LLM for on-device inference
- Target model: Qwen2.5-Coder-3B-Instruct, 4-bit quantized
- GitHub is the coordination layer — no custom backend
- Built under hackathon time constraints — favor working and simple over elegant and slow

## 2. Coding Standards
*(Add your team's actual conventions here — starter defaults below)*
- Kotlin: follow standard Android Kotlin style guide conventions
- Prefer small, single-purpose functions over large ones — easier to debug fast during a hackathon
- No dead code left in — comment it out only if you're actively deciding whether to keep it, delete otherwise

## 3. Tech Stack Constraints
*(What the agent must NOT suggest swapping, even if it seems "better")*
- Do not suggest Flutter, React Native, or Electron — decision is locked, see Project Plan
- Do not suggest a cloud LLM API as a fallback — on-device-only is the core pitch
- Do not introduce a new backend framework/server — GitHub API is the backend

## 4. Do's and Don'ts
- **Do** keep prompts to the on-device model short — diff-only context, not full files, for speed
- **Do** handle the "no self-hosted runner active" state gracefully (Red Light has no runner) rather than crashing
- **Don't** hardcode secrets/tokens in committed code — use local, gitignored config for the OAuth client ID during testing
- **Don't** over-engineer the UI — functional and clear beats polished and slow to build

## 5. Testing Requirements
- Any GitHub API integration change should be tested against the throwaway test repo before touching the real demo repo
- Any prompt change should be re-tested against the same 5–10 example diffs used during pre-hackathon prompt engineering, to catch regressions in output format

## 6. Personalization — Per-Developer Notes
*(Each team member can add their own working-style notes here — keep additions short and in your own subsection so they don't get lost or conflict with someone else's)*

### Member A
- *(e.g., "prefer terse commit messages", "always show me the diff before applying a fix")*

### Member B
- *(add your own preferences here)*

### Member C
- *(add your own preferences here)*

## 7. How To Extend This File
- New global rule: add to the relevant numbered section above, don't create a new top-level section unless it's a genuinely new category
- New personal preference: add under your own name in Section 6 only — don't put personal preferences in the shared sections, they'll get overwritten or conflict
- If a rule stops being true (e.g., you change model or framework), delete or update it immediately — a stale rule actively misleads an AI agent, which is worse than no rule at all
