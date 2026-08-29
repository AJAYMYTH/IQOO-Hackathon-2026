# Team Roles
## On-Device Repo Guardian — Pin-to-Pin Role Breakdown by Phase
**Team Apex OS · iQOO Hackathon 2026**

Roles are primary ownership, not silos — all three should understand the whole system. Assume roughly: Red Light ≈ first ~16.5 hrs, Green Light ≈ remaining ~13.5 hrs of the 30-hour window. **Confirm the actual phase schedule with organizers at Saturday check-in** — exact block timing hasn't been published, this plan assumes phases are contiguous, adjust if they're interleaved instead.

---

## Member A — Mobile / On-Device AI Lead

### Pre-Hackathon
- Get MLC-LLM's Android reference app building and running on a real Snapdragon device
- Convert/quantize Qwen2.5-Coder-3B and 1.5B, benchmark both, pick the primary
- Draft and test the analysis prompt (diff in, structured fix out) offline against the model

### Red Light
- Set up Termux dev environment on the phone, verify Office Kit remote-control workflow works for actual typing/editing
- Build the Splash, Auth, Repo Picker, Dashboard, and Review screens
- Wire MLCEngine calls into the Review Screen — this is the core, must work before anything else
- Integrate the voice trigger (`SpeechRecognizer`) on the Dashboard screen

### Green Light
- Move to Android Studio for any build/Gradle issues that were painful in Termux
- Polish inference speed, fix any UI bugs found during integration testing
- Support Member B on wiring PR Status Screen to real check-run data

### Final Sprint
- Own the live demo device — you know its exact state and quirks best
- Be the one holding the phone during the walkthrough

---

## Member B — Backend / Integration / DevOps Lead

### Pre-Hackathon
- Build and test the GitHub API client (device auth flow, list commits, diffs, branch/commit/PR creation, check-run status) against a throwaway repo
- Set up and rehearse the self-hosted GitHub Actions runner end-to-end on the laptop
- Prepare the dedicated demo repo: one reproducible planted bug, no existing CI/CD

### Red Light
- Since this phase is phone-only, focus on: refining the GitHub API glue code logic (as Kotlin/Termux-testable scripts) and the CI/CD-YAML-generation prompt, which Member A can call into once screens exist
- Support Member A's Termux setup if you're more comfortable with it

### Green Light
- Register/re-register the self-hosted runner against the real demo repo on venue WiFi
- Wire the PR Status Screen to real check-run data
- Full end-to-end test: commit → detect → PR → runner validates → status shown on phone
- Build the CI/CD Generator Screen's commit-workflow logic

### Final Sprint
- Own the backup plan if the runner or venue WiFi misbehaves live — have a recorded fallback clip of the validation loop working, just in case

---

## Member C — Product / Design / Pitch Lead

### Pre-Hackathon
- Finalize architecture diagram, deck skeleton (via the Replit master prompt), and video walkthrough script
- Prepare the "Why Us" narrative and rehearse framing that avoids revealing exact prompt/architecture specifics to competitors

### Red Light
- Since hands-on build is phone-focused, use this phase for: UI copy, in-app microcopy, testing the app as a first-time user on the phone as screens come online, refining the demo script against what's actually being built
- Draft the Settings/Rules screen content and default rule templates

### Green Light
- Record the final video walkthrough once the core loop works
- Finalize the deck with real screenshots/footage instead of mockups
- Rehearse the live demo with Member A at least twice, with a timer

### Final Sprint
- Run the pitch
- Make sure the Office Kit usage moment and voice-trigger moment are both explicitly narrated during the demo — these are scored rubric lines, don't let them pass silently
