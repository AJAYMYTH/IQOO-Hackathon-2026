# Product Requirements Document
## On-Device Repo Guardian — Phone-First Build
**Team Apex OS · iQOO Hackathon 2026 · Bengaluru City Battle**

---

## 1. Event Constraints This PRD Is Built Around

This isn't a generic hackathon PRD — every requirement below is shaped by the actual format and scoring rubric:

- **Red Light (55% of build time):** phone-only. No laptop compute. Office Kit provides screen mirror + remote control so the laptop is a monitor/keyboard for the phone, not a second machine.
- **Green Light (45% of build time):** phone + laptop combined workflows allowed.
- **HackTracker** logs actual device usage — Red Light work must be genuinely done on-device, not simulated.
- **Scoring weights:** 30% phone-native execution/end product quality, 15% creative camera/voice/NPU use, 10% meaningful Office Kit bridging, local/open-source model use earns rubric bonus points, plus real-world relevance and pitch quality.

The product plan below is designed to score well against every one of those, not just to "work."

## 2. Problem

Developers reviewing commits and maintaining CI/CD either do it manually (slow) or send code to a cloud AI service (privacy risk, ongoing cost). We're building an agent that reviews commits and proposes fixes using an LLM that runs entirely on the phone's NPU — provable, offline, no cloud dependency.

## 3. Goals

| Goal | Rubric line it serves |
|---|---|
| On-device LLM (llama.cpp + Qwen2.5-Coder) does real commit analysis, on-phone | Local/open-source model bonus, 30% phone-native execution |
| Voice command to trigger a review ("Hey, check the latest commit") | 15% creative NPU/camera/voice use |
| Office Kit used deliberately during Green Light for a real purpose (not just typing convenience) | 10% Office Kit bridging |
| App is fully usable and demoable on the phone alone | 30% phone-native execution |
| Real GitHub repo, real PR opened, real fix | Real-world relevance |

## 4. Target User

Solo developers and small teams who want AI-assisted code review and CI/CD generation without sending their code to a third-party cloud service.

## 5. Scope by Phase

### Red Light scope (phone-only — must be genuinely buildable via Termux + a mobile code editor)
- Kotlin glue code for GitHub API calls (auth, list commits, get diff, open PR)
- llama.cpp (via JNI/llama.android bindings) integration calls (prompt in, structured fix out)
- App UI screens (auth, repo picker, review screen)
- Voice-trigger integration (Android SpeechRecognizer API)
- Prompt engineering and testing against the on-device model

### Green Light scope (laptop assists)
- Full Android Studio builds, emulator testing, Gradle troubleshooting
- Self-hosted GitHub Actions runner setup and the test-validation loop
- Model conversion/quantization work if not using a pre-quantized GGUF weight
- Deck, video, diagram polish

## 6. Non-Goals

- No cloud LLM fallback — if it's not running on-device, it doesn't count toward your core pitch
- No multi-repo dashboard — one demo repo, done well, beats breadth
- No custom backend server — GitHub IS the backend

## 7. Success Metrics

- On-device inference works with no internet connection, demonstrated live
- A judge can watch the full loop (voice trigger → detection → PR → fix verified) in under 3 minutes
- Office Kit usage is visible and purposeful in the demo narrative, not incidental

## 8. Risks

| Risk | Mitigation |
|---|---|
| Compiling Android code via Termux during Red Light proves too painful | Pre-test this exact workflow before Saturday — don't discover it live |
| llama.cpp's Hexagon NPU backend (`ggml-hexagon`) is mainline but still tagged experimental — real-device behavior may vary by model size/op support | Benchmark all three backends (CPU, Adreno GPU, Hexagon NPU) on the actual iQOO 15 at check-in; keep CPU as your guaranteed fallback if NPU hits an unsupported op mid-demo |
| Voice trigger adds complexity for little payoff | Scope it to one hardcoded phrase → one action, not a full voice UI |
