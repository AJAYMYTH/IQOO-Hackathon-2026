# 🧠 Repo Guardian — Agent Memory File

> **Purpose:** This file contains the full project map, architecture, key file roles, and known issues so that AI agents can work efficiently without reading every file from scratch. **Read this first before touching anything.**

---

## Project Identity

- **Name:** Repo Guardian — On-Device AI Code Reviewer
- **Repo:** `https://github.com/AJAYMYTH/IQOO-Hackathon-2026.git`
- **Branch:** `main`
- **Package:** `com.apexos.repoguardian`
- **Target Device:** iQOO 15 (Snapdragon 8 Elite Gen 5, Hexagon NPU, Adreno 840 GPU)
- **Hackathon:** iQOO Hackathon 2026 — Bengaluru City Battle, Team Apex OS

---

## Tech Stack (Do NOT guess — these are exact)

| Layer | Tech | Version |
|-------|------|---------|
| Language | Kotlin | 2.1.0 |
| UI | Jetpack Compose + Material 3 | BOM 2025.05.00 |
| DI | Dagger Hilt + KSP | 2.54 / 2.1.0-1.0.29 |
| Network | Retrofit 2.11 + OkHttp 4.12 + Moshi 1.15 | — |
| Storage | AndroidX DataStore Preferences | 1.1.4 |
| AI Engine | llama.cpp (C++ via NDK JNI) | v0.3.0-dev |
| Build | AGP 8.7.3, CMake 3.22.1, NDK 27.2.12479018 | — |
| Min SDK | 28 (Android 9) | Target SDK 35 |

---

## Directory Map (Only What Matters)

```
Repo Guardian/
├── app/
│   ├── build.gradle.kts              ← App-level Gradle (deps, NDK config, CMake flags)
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt         ← Builds libllama_bridge.so, links llama.cpp
│       │   └── llama_bridge.cpp       ← JNI bridge: 7 native functions (load, context, generate, stream, free, info)
│       └── java/com/apexos/repoguardian/
│           ├── MainActivity.kt        ← Single activity, hosts NavGraph
│           ├── RepoGuardianApp.kt     ← Hilt Application class
│           ├── core/logging/
│           │   └── AppLogger.kt       ← In-memory log ring buffer (visible in Settings > Dev Logs)
│           ├── data/
│           │   ├── github/
│           │   │   ├── GitHubService.kt      ← Retrofit interfaces: GitHubAuthApi + GitHubDataApi
│           │   │   ├── GitHubRepository.kt   ← Data layer: wraps all GitHub API calls in ApiResult<T>
│           │   │   ├── GitHubAuthManager.kt  ← OAuth Device Flow (poll loop for token)
│           │   │   └── models/GitHubModels.kt ← ALL Moshi data classes (User, Repo, Commit, PR, Issue, Tree, etc.)
│           │   ├── huggingface/
│           │   │   ├── HuggingFaceService.kt      ← Retrofit interface for HF API
│           │   │   ├── HuggingFaceModels.kt       ← HF search result + file model data classes
│           │   │   ├── ModelDownloadManager.kt    ← Downloads GGUF models from HF CDN, tracks progress
│           │   │   └── ModelDownloadService.kt    ← Foreground service for background downloads
│           │   ├── llm/
│           │   │   ├── LlamaService.kt       ← ⭐ CENTRAL AI SERVICE: model loading, inference, local server fallback
│           │   │   ├── LlamaBridge.kt        ← JNI external function declarations (loadModel, generate, etc.)
│           │   │   ├── AiReasoningEngine.kt  ← Fallback response generator when no model is loaded
│           │   │   ├── PromptBuilder.kt      ← ChatML prompt construction (review, CI/CD, chat)
│           │   │   └── ReviewResult.kt       ← Moshi data classes for structured code review output
│           │   ├── preferences/
│           │   │   └── PreferencesManager.kt ← DataStore keys: token, repo, model path, backend, rules, server URL
│           │   └── voice/
│           │       └── VoiceService.kt       ← Android SpeechRecognizer for voice commands
│           ├── di/
│           │   └── AppModule.kt              ← Hilt module: provides Retrofit, OkHttp, Moshi instances
│           ├── navigation/
│           │   └── NavGraph.kt               ← All routes: splash→auth→repoPicker→dashboard→review→chat→cicd→settings→modelBrowser→prStatus
│           └── ui/
│               ├── auth/          AuthScreen.kt + AuthViewModel.kt
│               ├── chat/          ChatScreen.kt + ChatViewModel.kt       ← ⭐ Main AI chat with repo context
│               ├── cicd/          CiCdGeneratorScreen.kt + CiCdGeneratorViewModel.kt
│               ├── components/    AiThinkingIndicator.kt, MarkdownRenderer.kt, NonTechGuideDialog.kt
│               ├── dashboard/     DashboardScreen.kt + DashboardViewModel.kt  ← Commit list + voice FAB
│               ├── modelbrowser/  ModelBrowserScreen.kt + ModelBrowserViewModel.kt
│               ├── prstatus/      PrStatusScreen.kt + PrStatusViewModel.kt
│               ├── repopicker/    RepoPickerScreen.kt + RepoPickerViewModel.kt
│               ├── review/        ReviewScreen.kt + ReviewViewModel.kt   ← ⭐ Diff viewer + AI analysis
│               ├── settings/      SettingsScreen.kt + SettingsViewModel.kt
│               ├── splash/        SplashScreen.kt + SplashViewModel.kt
│               └── theme/         Color.kt, Theme.kt, Type.kt
├── llama.cpp/                     ← Git submodule (full llama.cpp repo)
│   ├── ggml/src/ggml-hexagon/     ← Snapdragon Hexagon NPU backend (NOT currently enabled in build)
│   ├── ggml/src/ggml-opencl/      ← Adreno GPU OpenCL backend (NOT currently enabled)
│   ├── ggml/src/ggml-vulkan/      ← Vulkan GPU backend (NOT currently enabled)
│   ├── ggml/src/ggml-cpu/         ← CPU backend with ARM NEON (THIS is what's active)
│   ├── docs/backend/snapdragon/   ← Official build docs for Hexagon NPU on Android
│   └── scripts/snapdragon/        ← Build scripts for Snapdragon cross-compilation
├── build.gradle.kts               ← Root Gradle (plugin versions only)
├── settings.gradle.kts            ← Module declaration (:app)
├── gradle.properties              ← JVM args, AndroidX flags
├── local.properties               ← SDK path + GITHUB_CLIENT_ID (gitignored)
└── TEAM_BUILD_AND_TEST_GUIDE.md   ← Setup instructions for team members
```

---

## How the AI Pipeline Works (Data Flow)

```
User selects repo → ChatViewModel.loadLiveRepositoryData()
    ├── GitHub API (7 parallel calls): getRepo, getRootContents, getReadme, listCommits, getGitTree, listIssues, listPulls
    └── Stores in LiveRepoContext (owner, name, language, files, readme, commits, issues, PRs)

User sends message → ChatViewModel.sendMessage()
    ├── Builds system prompt with: repo metadata + file tree + README + commits + dynamically retrieved source files
    ├── retrieveDynamicGitHubContext() → keyword-matches user query to repo files → fetches actual source code from GitHub
    └── llamaService.chatStream() →
        ├── Priority 1: Try local server URL (OpenAI/Ollama compatible HTTP endpoint)
        └── Priority 2: On-device native LLM via LlamaBridge JNI → llama.cpp C++ engine

Code Review flow → ReviewViewModel.loadAndReview()
    ├── gitHubRepository.getCommitDiff(owner, repo, sha) → gets patch text
    └── llamaService.reviewDiff(diffText) → returns ReviewResult JSON (has_issue, summary, issues[], fixed_code)
```

---

## Key Architecture Decisions

1. **Dual inference path:** LlamaService tries local HTTP server first (for dev with powerful desktop GPU), then falls back to on-device native LLM.
2. **ChatML prompt format:** `<|im_start|>system...<|im_end|>` — works with Qwen and Phi models. Llama 3 models use different template and may not work well.
3. **Model storage:** Downloaded GGUFs go to `context.filesDir/models/` (internal app storage).
4. **Backend preference:** Stored as string `"cpu"`, `"gpu"`, or `"npu"` in DataStore. Maps to `nGpuLayers` (0 for CPU, 33+ for GPU/NPU).
5. **Streaming:** JNI `generateStream` calls Kotlin lambda via `invoke` method reflection per token.

---

## Resolved Issues & Status (As of 2026-08-29)

### Fixed & Operational
1. ✅ **`isLoaded()` Fixed:** Now strictly verifies `modelHandle != 0L && contextHandle != 0L`, eliminating false positive "Active (Bridge Stubs)" loading state.
2. ✅ **Vulkan GPU Acceleration Enabled:** `-DGGML_VULKAN=ON` added to CMake and build.gradle.kts with `vulkan` library linkage for Adreno 840 on Snapdragon 8 Elite.
3. ✅ **Flash Attention Enabled:** `ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED` in `llama_bridge.cpp` for 30-50% speedup.
4. ✅ **Thread Safety Resolved:** `unload()` now safely acquires `inferenceMutex` and `loadMutex` before freeing C++ handles.
5. ✅ **Context Window Expanded (4096 tokens):** `createContext` updated to 4096 tokens, preventing prompt truncation during deep repository context analysis.
6. ✅ **Response Timing & Throughput Instrumentation Added:** High-resolution timers measure prompt evaluation time, token generation throughput (tok/s), and total wall-clock latency with live badges displayed in Review and Chat screens.
7. ✅ **Dynamic Default Branch:** `createFixPr` now queries and respects repository's default branch (`main`, `master`, or custom).
8. ✅ **`UpdateFileRequest.sha` Nullable:** Fixed HTTP 422 errors when creating new files via GitHub contents API.
9. ✅ **Diff Truncation Improved:** Limit increased from 4,000 to 10,000 chars with line/hunk boundary-aware truncation.
10. ✅ **Scoped Storage Protected:** Auto-discovery safely wraps external storage access.

---

## Hardware Acceleration Status

| Backend | In llama.cpp | Compiled in APK | CMake Flag Needed |
|---------|:---:|:---:|---|
| CPU (ARM NEON) | ✅ | ✅ | Always on |
| Vulkan (Adreno 840) | ✅ `ggml-vulkan` | ✅ | `-DGGML_VULKAN=ON` (Active & Linked) |
| Hexagon NPU | ✅ `ggml-hexagon` | ⚙️ Ready | `-DGGML_HEXAGON=ON` + Hexagon SDK |
| OpenCL (Adreno) | ✅ `ggml-opencl` | ⚙️ Ready | `-DGGML_OPENCL=ON` + OpenCL headers |

**For iQOO 15:** Hexagon NPU gives best perf (~51 tok/s for 1B model) but needs Qualcomm QNN SDK. Vulkan is the easiest to enable (no proprietary SDK).

---

## Files You Probably Need to Edit (By Task)

| Task | Files |
|------|-------|
| Fix AI inference | `LlamaService.kt`, `llama_bridge.cpp` |
| Enable GPU/NPU | `app/src/main/cpp/CMakeLists.txt`, `app/build.gradle.kts` |
| Change prompts | `PromptBuilder.kt` |
| Add GitHub API calls | `GitHubService.kt` (interface), `GitHubRepository.kt` (wrapper), `GitHubModels.kt` (data classes) |
| Modify chat UI | `ChatScreen.kt`, `ChatViewModel.kt` |
| Modify review flow | `ReviewScreen.kt`, `ReviewViewModel.kt` |
| Change model downloads | `ModelDownloadManager.kt`, `HuggingFaceService.kt` |
| Settings/preferences | `PreferencesManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` |
| Navigation/routes | `NavGraph.kt` |
| Add new screen | Create `ui/newscreen/`, add route in `NavGraph.kt` |

---

## Files You Should NEVER Need to Read

- `app/build/generated/**` — Auto-generated Hilt/KSP code
- `llama.cpp/` internals (unless enabling a new backend) — it's a submodule
- `.gradle/`, `.idea/`, `.kotlin/` — IDE/build cache
- `docs/`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` — project docs, not code

---

*Last updated: 2026-08-29 by analysis session. 47 source files fully analyzed.*
