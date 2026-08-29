# Team Collaboration
## On-Device Repo Guardian — How We Work Together
**Team Apex OS · iQOO Hackathon 2026**

---

## 1. Communication Cadence

- **Sync every 4 hours** during the 30-hour window, 5 minutes max: what's done, what's blocked, what's next. Longer discussions happen async or get scheduled separately, not during the sync.
- **Immediately flag blockers** in the shared chat rather than sitting on them — with 3 people and a hard clock, an hour lost silently is an hour the whole team loses.
- One person (suggest Member C, since they're least deep in low-level implementation) keeps a lightweight running log of decisions in `Memory.md` (see that file) so context isn't lost between syncs.

## 2. Git Workflow

- One shared repo, feature branches per screen/module (`feat/auth-flow`, `feat/review-screen`, `feat/pr-status`)
- Small, frequent commits over large ones — easier to review quickly and easier to revert if something breaks mid-Red-Light when debugging options are more limited
- Direct merges to `main` are fine for a 3-person hackathon team — skip heavyweight PR review process for your own team's commits; save that ceremony for what the AI agent does to the *demo* repo, which is a separate, deliberately real GitHub flow

## 3. Red Light Coordination

Since only phones are usable, coordinate who's building which screen to avoid stepping on the same files. Recommended split during Red Light:
- Member A: Review Screen + MLCEngine wiring (the critical path — should not be blocked waiting on anyone)
- Member B: GitHub API client logic (can be built/tested somewhat independently as Kotlin functions before UI exists)
- Member C: UI copy, Settings/Rules screen content, demo script refinement

Use Office Kit's shared clipboard/file transfer to pass code snippets or test data between phone and laptop displays if one person needs to show another something quickly.

## 4. Green Light Coordination

This is when integration happens — the three independently-built pieces (UI, GitHub client, on-device inference) come together. Expect this to surface the most bugs. Order of integration:
1. GitHub client + Review Screen (Member A + B pair directly)
2. PR Status Screen + self-hosted runner (Member B leads, A supports)
3. Full end-to-end test, all three present

## 5. Decision-Making

- Technical decisions within a role's ownership: that member decides, others can flag concerns but don't need to approve
- Cross-cutting decisions (scope cuts, demo script changes, what to drop if behind schedule): quick 3-way call, majority wins, don't let disagreement eat build time
- If genuinely stuck on a decision for more than 10 minutes, default to whatever keeps the core loop (commit → detect → PR → verify) working — that's the non-negotiable demo spine per the Project Plan

## 6. When Things Go Wrong

- If someone's blocked for more than 30 minutes, say so out loud immediately rather than quietly debugging alone — with 3 people, someone else may unblock it faster
- Protect sleep in shifts if the schedule allows — a tired debugging session late in a 30-hour window costs more time than it saves
- The Implementation Plan's fallback section (simplify UI, don't pivot frameworks) is the team's agreed answer if things go sideways — no re-litigating that decision live under stress
