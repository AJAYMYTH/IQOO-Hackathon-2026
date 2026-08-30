# 🚀 Repo Guardian — Master Presentation Prompt for Replit

> **Hackathon:** iQOO Hackathon 2026 — Bengaluru City Battle  
> **Team:** Team Apex OS  
> **Project:** Repo Guardian (Autonomous On-Device AI Code Reviewer)  
> **Repository:** `AJAYMYTH/IQOO-Hackathon-2026`

---

## 📋 How to Use on Replit
1. Open [Replit](https://replit.com) and click **Create Repl** (Choose **React / Vite + Tailwind** or start with **Replit Agent**).
2. Paste the **Master Prompt** below into the prompt box.
3. Replit will generate a full, interactive, animated slide deck with keyboard navigation (`Left`/`Right`/`Space`), tabs, live diff comparison, and benchmark visualizers.

---

```markdown
Create a modern, high-impact, interactive web-based pitch slide deck application for "Repo Guardian — On-Device AI Code Reviewer" built for the iQOO Hackathon 2026 (Bengaluru City Battle) by Team Apex OS.

### 🎨 Design System & Theme
- **Theme:** Ultra-sleek Developer Dark Theme / Cyber Slate aesthetic.
- **Palette:** 
  - Background: Deep Slate Canvas (`#0A0F1D` / `#0D1117`)
  - Elevated Cards: `#161B22` with subtle borders (`#30363D`)
  - Primary Accent: Electric Emerald / Terminal Green (`#10B981` / `#00E599`)
  - Secondary Accent: Indigo / Cyber Violet (`#6366F1`)
  - Status Colors: Critical Red (`#EF4444`), Warning Amber (`#F59E0B`), Pass Green (`#10B981`)
- **Typography:** Inter / Plus Jakarta Sans for headings, JetBrains Mono / Fira Code for code blocks and metrics.
- **Animations:** Smooth slide transitions using Framer Motion / CSS transitions, glowing badges, subtle particle/grid background.

### 🕹️ Interactive Features
1. **Keyboard & Touch Navigation:** Left/Right arrow keys, Spacebar, Swipe on mobile, and an on-screen bottom navigation bar with progress indicator (Slide X of 11).
2. **Interactive Elements:**
   - Interactive Architecture Diagram tabs (UI Layer, Business Logic, JNI Bridge, Hardware Acceleration).
   - Live Diff Comparison Toggle (Buggy Diff vs. AI-Generated Remediation Patch).
   - Dynamic Benchmark & Token Speed Visualizer (CPU vs Adreno GPU vs Hexagon NPU).
   - Fullscreen toggle button (`F` key) and Presenter Mode with speaker notes toggle (`S` key).

---

### 📑 Slide Deck Structure (11 Slides)

#### Slide 1: Title & Hook (Hero)
- **Title:** 🛡️ Repo Guardian
- **Subtitle:** Autonomous On-Device AI Code Reviewer & Repository Sentinel
- **Tagline:** 100% Offline • Hardware-Accelerated • Zero Cloud Costs • Total Code Privacy
- **Event & Team:** iQOO Hackathon 2026 — Bengaluru City Battle | By Team Apex OS
- **Badges:** Android 9+ (API 28–35) | Kotlin & Compose | llama.cpp Native JNI | Qwen2.5-Coder-3B
- **Speaker Note:** "Welcome judges! Today we present Repo Guardian — an autonomous on-device AI system that brings enterprise-grade code review directly onto your smartphone with zero cloud dependencies."

#### Slide 2: The Problem (Why Cloud AI Code Review Fails for Developers)
- **Security & Privacy Leaks:** Enterprise & proprietary source code diffs sent to third-party cloud APIs violate compliance and IP security.
- **High Cloud Ingestion Costs:** High per-token API fees for scanning every git commit diff across active repositories.
- **Network Dependency:** No mobile code review capability on flights, remote locations, or offline environments.
- **Disconnected Mobile Experience:** Developers cannot easily inspect commits, generate fixes, and submit Pull Requests natively from Android.
- **Speaker Note:** "Every time developers use cloud AI for code review, confidential company code is transmitted over the internet. Furthermore, token bills scale with every single commit diff."

#### Slide 3: The Solution (Repo Guardian)
- **Core Value:** Turns the smartphone into an autonomous edge AI code review agent running lightweight LLMs on bare-metal mobile silicon.
- **4 Key Pillars:**
  1. *Total Data Sovereignty:* Diffs never leave local RAM.
  2. *Hardware Acceleration:* Native ARM NEON, Adreno GPU OpenCL, and Qualcomm Hexagon NPU offloading.
  3. *Full Git-GitHub Loop:* Voice / Tap Trigger → Diff Analysis → Patch Remediation → One-Tap PR → CI/CD Check-Runs.
  4. *Zero Operating Cost:* Unlimited code reviews with $0 API token costs.
- **Speaker Note:** "Repo Guardian solves this completely. By running quantized LLMs on-device, code diffs never leave the phone, latency drops, and cloud API bills are reduced to zero."

#### Slide 4: System Architecture (Deep Technical Breakdown)
- **Layer 1 (UI):** 100% Jetpack Compose + Material 3, Dark Slate Dev Theme, 9 MVI ViewModels.
- **Layer 2 (Services):** `LlamaService` (Coroutines inference pipeline), `VoiceService` (Intent router), `GitHubAuthManager` (Device Flow).
- **Layer 3 (Native Bridge):** Custom `llama_bridge.cpp` JNI, non-blocking streaming token evaluation, dynamic context degradation (4096 → 2048 → 1024 → 512).
- **Layer 4 (Hardware Engine):** `llama.cpp` + GGML engine executing quantized GGUF models directly on Snapdragon cores.
- **Speaker Note:** "Our architecture bridges modern Jetpack Compose UI with bare-metal C++ execution through an optimized JNI layer."

#### Slide 5: The On-Device LLM & Native JNI Innovation
- **Why llama.cpp over Python/Cloud:** Pure bare-metal C++ execution, ~1.8 GB RAM footprint for 3B coder models, sub-2.5s time-to-first-token.
- **What We Built on Top:**
  - *Custom JNI Bridge:* Kotlin `Flow<String>` token streaming with real-time UI rendering.
  - *ChatML Engine:* Optimized prompt compression adhering to strict JSON output schemas.
  - *Universal ARM64 Compatibility:* Multi-tier fallback preventing illegal instruction (`SIGILL`) crashes across chipsets.
- **Speaker Note:** "We engineered a custom JNI bridge with progressive context degradation to ensure reliable execution across both flagship and budget devices."

#### Slide 6: Live Workflow & User Journey (Step-by-Step)
- **Step 1: Seamless Auth:** Passwordless GitHub OAuth Device Flow (zero callback server needed).
- **Step 2: Hands-Free Voice Trigger:** Natural speech trigger (*"Hey, review the latest commit"*).
- **Step 3: Severity-Based Issue Triage:** Automatically tags issues into **Critical** (🚨), **Warning** (⚠️), and **Info** (ℹ️).
- **Step 4: Autonomous PR Submission:** Generates git branch, commits remediation code, and creates GitHub Pull Request in 1 tap.
- **Step 5: Live CI/CD & Generator:** Monitors GitHub Actions check-runs and generates `.github/workflows/ci.yml` tailored to repo language.
- **Speaker Note:** "The developer can trigger a review via voice, inspect the diff, tap 'Create Fix PR', and watch GitHub Actions check-runs execute in real time."

#### Slide 7: Interactive Diff & Remediation Showcase
- **Side-by-Side Comparison:**
  - *Original Git Diff:* Unhandled NullPointer / SQL Injection / Resource Leak in Kotlin/Java.
  - *Repo Guardian Analysis:* Explains the vulnerability root cause with exact line references.
  - *AI Suggested Patch:* Clean, production-ready code fix ready for auto-PR merge.
- **Speaker Note:** "Here is a live example of a SQL vulnerability detected in a commit diff. Repo Guardian pinpoints the line, generates the parameterized query fix, and stages the commit."

#### Slide 8: Hardware Performance & Benchmark Metrics
- **Quantization:** GGUF Q4_K_M (Qwen2.5-Coder-3B-Instruct / DeepSeek-R1-Distill-1.5B).
- **Inference Speed:**
  - *CPU (ARM NEON):* 14.5 – 18.2 tokens/sec
  - *GPU (Adreno OpenCL):* 24.0 – 29.5 tokens/sec
  - *NPU (Qualcomm Hexagon HTP):* 32.0 – 38.0 tokens/sec
- **Memory Footprint:** ~1.8 GB VRAM/RAM (Runs smoothly within Android's 4GB–12GB memory envelope).
- **First Token Latency:** < 1.8 seconds.
- **Speaker Note:** "On modern Snapdragon silicon, our NPU offloading delivers up to 38 tokens per second, making code review feel instant and responsive."

#### Slide 9: In-App Hugging Face Hub & Model Management
- **In-App Model Discovery:** Search, filter (≤4GB GGUF), and download coder models directly within the app.
- **Background Download Service:** Foreground service with resumable byte-stream downloading and notification progress.
- **Local Server Bridge:** Seamlessly toggle between On-Device Mobile LLM and LAN Local AI servers (Ollama / LM Studio / llama.cpp server).
- **Speaker Note:** "Developers can switch models on the fly using our in-app Hugging Face hub, or connect to their office local server over Wi-Fi."

#### Slide 10: Market Impact, Enterprise Security & Business Value
- **Target Audience:** Mobile Devs, On-Call Engineers, Open Source Maintainers, Privacy-Conscious Enterprise Teams (Finance, Healthcare, Defense).
- **Cost Reduction:** Replaces $20–$50/seat/mo cloud code review subscriptions with free on-device compute.
- **Developer Velocity:** Review commits on the go during transit or emergencies without opening a heavy laptop.
- **Speaker Note:** "Repo Guardian democratizes AI code review for privacy-conscious organizations, saving thousands of dollars in cloud SaaS subscriptions."

#### Slide 11: Conclusion & Live Demo Q&A
- **Key Takeaway:** Edge AI is here. Powerful, private, hardware-accelerated code intelligence in the palm of your hand.
- **Live Demo Checklist:**
  1. Voice trigger on recent commit diff.
  2. Real-time on-device token streaming.
  3. Automatic GitHub Pull Request creation.
  4. CI/CD workflow generation.
- **Open Source:** Apache 2.0 License • GitHub: `AJAYMYTH/IQOO-Hackathon-2026`
- **Thank You & Q&A:** Team Apex OS | iQOO Hackathon 2026.
- **Speaker Note:** "Thank you judges! We are now ready to demonstrate Repo Guardian running live on physical hardware."

---

### 💻 Technical Implementation Details for Replit
- Built as a React SPA with Tailwind CSS and Lucide React icons.
- Include interactive toggleable widgets for Slide 4 (Architecture tabs), Slide 7 (Diff view), and Slide 8 (Benchmark charts).
- Ensure high responsiveness for 16:9 projection displays and mobile preview.
```
