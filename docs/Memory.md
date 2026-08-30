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

# Memory.md — Project Memory Log
## Purpose

This file is the single source of truth for "what's actually built and decided so far."

---

### Current Status — Complete & Verified (Release v3.1.0)
- **Auth Flow:** ✅ Working (GitHub OAuth Device Flow with continuous background polling, clipboard auto-copy, and lifecycle cancellation on exit).
- **Repo Picker Screen:** ✅ Working (`GET /user/repos`, search filtering, private/public indicator, language chips).
- **Dashboard Screen:** ✅ Working (Commit timeline, monospace SHAs, author metadata, pulsing voice FAB).
- **On-Device Inference:** ✅ Working (`llama.cpp` + custom `llama_bridge.cpp` JNI, Qwen2.5-Coder-3B Q4_K_M, multi-tier context degradation 4096 $\rightarrow$ 2048 $\rightarrow$ 1024 $\rightarrow$ 512, sub-1.8s first token).
- **Hardware Acceleration:** ✅ Working (CPU ARM NEON, Adreno GPU OpenCL, Qualcomm Hexagon NPU router with CPU fallback).
- **Review & Diff Inspector:** ✅ Working (Syntax-highlighted diffs, severity triage [Critical/Warning/Info], automated remediation patch, markdown report export).
- **GitHub PR Creation:** ✅ Working (Auto branch creation on default branch, patch commit, PR submission with markdown summary).
- **PR Status Screen:** ✅ Working (Live check-run polling with self-hosted runner and graceful offline state).
- **AI Codebase Assistant:** ✅ Working (Dynamic RAG with live GitHub repo tree indexing, README, commits, and source code snippet injection).
- **CI/CD Generator Screen:** ✅ Working (Automatic stack detection, editable YAML card, 1-tap clipboard copy, and direct `.github/workflows/ci.yml` commit).
- **Settings & Rules:** ✅ Working (Custom `.repoguardian/rules.md` editing, backend selector, LAN local server URL, in-app dev logs viewer).
- **In-App Model Browser:** ✅ Working (Hugging Face Hub search, ≤4GB GGUF filter, foreground download service with progress).
- **Voice Trigger:** ✅ Working (Android SpeechRecognizer intent router with offline + online fallback).

---

### Decisions Log
- **2026-08-28:** Selected `llama.cpp` + GGML C++ core over heavy Python runtimes for bare-metal ARM64 execution and native Qualcomm Snapdragon Hexagon NPU offloading.
- **2026-08-29:** Selected `Qwen2.5-Coder-3B-Instruct` (Q4_K_M GGUF) as primary model (~1.8 GB RAM footprint, < 1.8s TTFT, 18-38 tok/s).
- **2026-08-30:** Implemented dynamic context degradation (4096 $\rightarrow$ 2048 $\rightarrow$ 1024 $\rightarrow$ 512 tokens) and Flash Attention fallback to prevent memory allocation crashes across diverse RAM capacities (3GB–16GB).
- **2026-08-30:** Removed artificial 5s/30s client-side login countdowns and replaced with continuous background polling and automatic lifecycle cancellation on app exit.
- **2026-08-30:** Added one-tap markdown review report export and CI/CD YAML copy actions to facilitate seamless Office Kit bridging between phone and laptop.

---

### Demo Path (Live 3-Minute Walkthrough)
1. **Splash $\rightarrow$ Dashboard:** Open app with pre-authenticated demo repo.
2. **Voice Trigger / Tap:** Say *"Hey, review the latest commit"* or tap top commit card.
3. **Review & Severity Triage:** Observe on-device token streaming, line-level vulnerability identification, and remediation patch.
4. **Create PR:** Tap "Create Pull Request from Fixes" $\rightarrow$ open GitHub PR $\rightarrow$ verify check-runs in PR Status.
5. **CI/CD Generator & Chat:** Quick switch to CI/CD Generator to auto-commit `.github/workflows/ci.yml` and AI Chat for repo Q&A.

