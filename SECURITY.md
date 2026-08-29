# Security Policy 🔒

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## 🛡️ On-Device Privacy Guarantees

Repo Guardian is architected with a strict **Privacy-First, On-Device AI** principle:

1. **Zero Cloud Code Telemetry:** All code diff analysis, syntax evaluations, and fix suggestions are executed entirely on your mobile device's processor/NPU via `llama.cpp`. No code snippets are ever transmitted to third-party AI APIs (OpenAI, Anthropic, etc.).
2. **Secure Token Storage:** GitHub OAuth tokens are stored locally on-device in Android's encrypted DataStore preferences and are never logged or exported.
3. **No Central Backend:** The app talks directly to GitHub's official REST API (`https://api.github.com`) over TLS 1.3.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in Repo Guardian, please do **NOT** open a public GitHub issue.

Instead, please report it privately:
- Email: **security@apexos.dev** (or contact the repository maintainers directly on GitHub).
- Include detailed steps to reproduce the issue, the affected component, and potential mitigations.

We will acknowledge receipt within 48 hours and work with you on a responsible disclosure timeline.
