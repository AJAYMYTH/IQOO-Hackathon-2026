# Technical Requirements Document
## On-Device Repo Guardian — Phone-First Build
**Team Apex OS · iQOO Hackathon 2026**

---

## 1. Stack Summary

| Layer | Choice |
|---|---|
| On-device inference | **llama.cpp** (via `llama.android` JNI bindings) |
| Model | Qwen2.5-Coder-3B-Instruct (fallback: 1.5B), 4-bit GGUF quantized (Q4_K_M) |
| App | Native Android (Kotlin), forked from llama.cpp's `examples/llama.android` reference app |
| Red Light dev environment | **Termux** on the phone + a mobile code editor, screen/keyboard via Office Kit |
| Green Light dev environment | Android Studio on laptop, full Gradle/NDK toolchain |
| Coordination | GitHub REST/GraphQL API (device OAuth flow) |
| Test validation | Self-hosted GitHub Actions runner on laptop (Green Light only) |
| Voice trigger | Android `SpeechRecognizer` API (on-device, no cloud STT) |

---

## 2. Detailed Guide: Getting llama.cpp Running (Pre-Hackathon)

Do this entire section **before Saturday** — model conversion/download and first builds are slow and not something to discover live.

### 2.1 Clone and set up
```bash
git clone https://github.com/ggml-org/llama.cpp.git
cd llama.cpp
```
The Android reference project lives at `examples/llama.android` — this is what you'll import into Android Studio.

### 2.2 Get the model in GGUF format
If Qwen2.5-Coder-3B-Instruct isn't already published as a GGUF on Hugging Face (search the HF hub — quantized GGUF community uploads are common for popular models), convert it yourself:

```bash
# Requires Python + llama.cpp's conversion scripts — do this on a laptop, not the phone
python convert_hf_to_gguf.py ./Qwen2.5-Coder-3B-Instruct --outfile qwen2.5-coder-3b.gguf --outtype f16

# Quantize down to 4-bit
./llama-quantize qwen2.5-coder-3b.gguf qwen2.5-coder-3b-q4_k_m.gguf Q4_K_M
```
Keep the resulting `.gguf` file — you'll bundle or side-load this onto the phone (either packaged in the app's assets if size allows, or pushed via `adb push` / Office Kit's file bridge to the app's local storage).

### 2.3 Build the Android app
```bash
cd examples/llama.android
```
Open this directory in Android Studio, let Gradle/CMake sync (it builds `llama.cpp`'s C++ core via NDK/CMake automatically), then build and install to a real Snapdragon device — not an emulator, since GPU/NPU-relevant acceleration paths don't reflect real performance there.

### 2.4 Point the app at your model
The reference app loads a `.gguf` file path at runtime through its JNI bridge to `llama.cpp`'s C API (`llama_model_load_from_file` under the hood). Update the reference app's model-loading code to point at wherever you've placed your quantized `.gguf` file on-device, and confirm it loads and generates output before doing anything else.

### 2.5 Strip it down to your app
The reference app is a minimal chat/benchmark UI — you don't need that. Keep the JNI bridge and inference-calling code (the `llama.cpp` context setup, tokenizer, and generation loop), replace the UI with your own (auth, repo picker, review screen). Your prompt-construction code (diff in, structured fix out) plugs in wherever the reference app currently sends its chat prompt into the generation loop.

### 2.6 Acceleration backend — Hexagon NPU
llama.cpp has an official, mainline **Hexagon NPU backend** (`ggml-hexagon`), documented at `docs/backend/snapdragon/README.md` in the repo. It supports three backends on Snapdragon devices — CPU, Adreno GPU (`GPUOpenCL`), and Hexagon NPU (`HTP0`–`HTP4`) — selectable via the `D=` device variable (maps to `--device`). This is a genuine reason to prefer llama.cpp: real NPU offload, not just GPU.

Practical notes:
- It's still tagged **experimental** upstream — test it directly on your target device rather than assuming full op coverage for your exact model
- If you hit unsupported ops or instability, `chraac/llama-cpp-qnn-builder` is a more NPU-focused community build worth trying as a fallback (built on Qualcomm's QNN SDK + custom FastRPC/HVX kernels, reports 2–5x better performance-per-watt vs. CPU)
- Build with `-DGGML_HEXAGON=ON` (check current CMake flag name in the repo docs, this has moved between releases) and select the NPU device at runtime with `D=HTP0`
- **Benchmark all three backends (CPU/GPU/NPU) on the real iQOO 15 before Saturday** and pick your default based on actual tokens/sec and stability — don't assume NPU is automatically fastest for a 3B model until you've measured it, and keep CPU as your safe fallback if the NPU path misbehaves live

---

## 3. Red Light Dev Workflow (Phone-Only)

This is the part most teams underestimate. Practice this exact flow before Saturday:

1. Install **Termux** on the phone (or your test device) from F-Droid
2. Set up a basic dev environment inside Termux: `pkg install git nodejs python` etc., as needed for your Kotlin/API glue scripting and testing
3. Use **Office Kit** to mirror the phone screen to the laptop and enable remote keyboard/mouse input — this is your "monitor and keyboard," the laptop does no computation
4. Write and test your GitHub API client logic, prompt templates, and app UI code changes through this bridged setup
5. For anything requiring a real Kotlin/Gradle build (not just scripting/testing), have a plan for how much of that is genuinely Termux-feasible vs. needs to wait for Green Light — command-line Gradle builds via Termux + Android SDK command-line tools are possible but slower and fiddlier than Android Studio; budget time accordingly and don't assume it "just works"

## 4. Voice Trigger Integration (15% rubric line)

Use Android's built-in `SpeechRecognizer` (fully on-device on modern Android, no network call required for basic recognition on many devices — verify on the actual iQOO 15). Scope: one clear trigger phrase → one action (e.g., "review latest commit" → kicks off the analysis pipeline). Don't build a general voice UI; a single, reliable trigger reads as more polished in a live demo than a shaky general voice assistant.

## 5. Office Kit Integration (10% rubric line)

Make sure Office Kit usage is *visible and purposeful* in your demo, not incidental:
- Use its file-transfer/clipboard bridge to move something meaningful between phone and laptop during the Green Light portion of your demo (e.g., pulling a generated report or log off the phone)
- Narrate it explicitly in your pitch: "here's where we use Office Kit to move the validation results back to the phone" — judges are told this is scored from device data, so make the moment clear rather than assuming they'll notice

## 6. GitHub Integration

(Covered in depth in earlier planning — summary here for completeness)
- OAuth **device flow** for auth (no callback URL needed)
- REST API for commits, diffs, branch creation, PR creation, check-run status
- Self-hosted Actions runner on the laptop for test validation, registered and tested before Saturday, active only during Green Light

## 7. Performance Targets

- First-token latency under ~3 seconds on the real device for a demo-sized diff (not a huge file)
- Full "detect → propose fix" cycle under 15 seconds for the live demo to feel responsive
- If Qwen2.5-Coder-3B doesn't hit this, fall back to the 1.5B variant — decide this during pre-hackathon benchmarking, not live
