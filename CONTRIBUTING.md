# Contributing to Repo Guardian 🛡️

First off, thank you for considering contributing to **Repo Guardian**! We welcome contributions from solo developers, open-source enthusiasts, and AI researchers.

---

## 📋 Code of Conduct

This project and everyone participating in it is governed by the [Repo Guardian Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## 🛠️ Development Setup

1. **Prerequisites:**
   - **Android Studio** (2024.1+ / Ladybug / Koala)
   - **Android SDK** with API 35 (compileSdk: 35, minSdk: 28)
   - **Android NDK** (`27.2.12479018` or `27.x`)
   - **CMake** (`3.22.1`+)
   - **Git** with submodule support

2. **Clone & Initialize Submodules:**
   ```bash
   git clone https://github.com/AJAYMYTH/IQOO-Hackathon-2026.git
   cd IQOO-Hackathon-2026
   git submodule add https://github.com/ggml-org/llama.cpp.git llama.cpp
   ```

3. **Configure Local Properties:**
   Create a `local.properties` file in the project root:
   ```properties
   sdk.dir=C\:\\Users\\<your_user>\\AppData\\Local\\Android\\Sdk
   GITHUB_CLIENT_ID=your_github_oauth_app_client_id
   ```

4. **Build the Project:**
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🌿 Branching & Git Workflow

- Create feature branches from `main`:
  ```bash
  git checkout -b feat/your-feature-name
  ```
- Use descriptive commit messages following the [Conventional Commits](https://www.conventionalcommits.org/) format:
  - `feat: Add support for Vulkan GPU inference backend`
  - `fix: Handle network timeout during check-run polling`
  - `docs: Update on-device benchmark metrics`

---

## 📐 Coding Standards

- **Kotlin:** Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and Android Kotlin style guide.
- **Architecture:** Maintain unidirectional data flow (MVI/MVVM) with `@HiltViewModel`, `StateFlow`, and Jetpack Compose `@Composable` functions.
- **On-Device LLM:**
  - Keep prompts token-efficient (diff-only context).
  - Do NOT introduce dependencies on external cloud LLM APIs.
- **Secrets:** Never commit API keys, GitHub tokens, or private secrets. Use `local.properties` and `BuildConfig`.

---

## 🧪 Submitting a Pull Request

1. Push your branch to GitHub:
   ```bash
   git push origin feat/your-feature-name
   ```
2. Open a Pull Request against `main`.
3. Provide a clear description of your changes, screenshots for UI changes, and testing notes.
4. Ensure CI checks and `./gradlew check` pass.

Thank you for helping make on-device AI code review faster, smarter, and private for everyone!
