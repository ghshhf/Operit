# Contributing Guide (English) · ghshhf/Operit

> This is the contributor entry point for `ghshhf/Operit`, the **engineering-hardened fork**.
> 中文版见 [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md)；
> build details in [BUILDING.md](BUILDING.md)；architecture in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)；
> strategy in [docs/ROADMAP.md](docs/ROADMAP.md).

---

## 1. What this fork is

`ghshhf/Operit` is a community fork of upstream [`AAswordman/Operit`](https://github.com/AAswordman/Operit),
positioned as the **"engineering-hardened edition"**:

> **Upstream imagines; we make it actually run.**

We are not "a faster-syncing upstream". We are the branch that turns **CI gates, automated tests,
docs, monthly releases, and contributor-friendliness** into a moat. A change earns the "advanced
edition" label only if it makes the build more reliable, the tools more stable, or onboarding easier.

---

## 2. sync-first principle (required reading)

We follow **sync-first**: when upstream updates, we `gh repo sync` it into our `main` first, then
build our fixes on top.

- **Do not** rebase our fork logic onto upstream; keep `main` tracking upstream + our patches.
- Any modification to an **upstream file** MUST be recorded in [`PATCHES.md`](PATCHES.md)
  (issue / file / commit / re-verify test) so it can be re-checked after each sync.
- Our unique enhancements (CI, docs, tests, ops) live in files upstream rarely touches
  (`.github/`, `docs/`, `app/src/test/`, `.devcontainer/`), so they rarely conflict.
- When fixing an upstream bug, **also open a PR upstream** (upstream-first). Once merged, that
  divergence disappears and syncing gets easier.

---

## 3. Environment setup (running in 30 minutes)

Full steps in [BUILDING.md](BUILDING.md). Quick view:

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 17 (Temurin) | compile |
| Android SDK | Platform 36 / Build-Tools 36.0.0 | compile |
| NDK | 27.0.12077973 | native modules (quickjs / terminal / ncnn…) |
| CMake | 3.22.1 | native build |
| Node.js | 20 | web-chat / tooling scripts |
| Python | 3.12 + venv | sync scripts |

Easiest path: use the bundled **Dev Container** (`.devcontainer/`) to spin up the whole toolchain.

```bash
git clone https://github.com/ghshhf/Operit.git
cd Operit
git submodule update --init --depth 1 \
  terminal mnn/src/main/cpp/MNN llama/third_party/llama.cpp \
  mmd/third_party/saba mmd/third_party/bullet3 \
  app/src/main/cpp/thirdparty/ncnn app/src/main/cpp/thirdparty/sherpa-ncnn \
  quickjs/thirdparty/quickjs fbx/third_party/ufbx
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

> ⚠️ The `tools/hotbuild/OperitNightlyRelease` submodule uses an SSH URL and is NOT a compile
> dependency — **do not initialise it**.

---

## 4. Develop & open a PR

1. Branch from `main`: `git checkout -b fix/short-description`.
2. Code + **write/update a regression test** (see §5).
3. Local self-test: `./gradlew assembleDebug testDebugUnitTest`.
4. Commit messages follow **Conventional Commits** (see §6).
5. Push to your fork and open a PR against `ghshhf/Operit:main`.
6. Wait for **CI (build + lint) to be green**; fill the PR template and link the issue.
7. Maintainer reviews → merge.

---

## 5. Tests are mandatory (not optional)

Our "no regressions" promise is defended by tests.

- **Location**: JVM unit tests go in `app/src/test/` (no emulator needed, CI-runnable).
- **Required**: every bug fix ships a regression test that pins the "it used to break" behaviour.
- Run: `./gradlew testDebugUnitTest`.
- No Android device? Prefer JVM-level tests (tool param parsing, JSON serialization, memory
  read/write, MCP codec, …).

---

## 6. Commit message convention (Conventional Commits)

```
<type>(<scope>): <subject>
```

Common `type`: `feat` / `fix` / `docs` / `chore` / `refactor` / `test` / `style`.
Examples:

```
fix(tools): tolerate literal quotes in package_proxy params (#657)
feat(ui): one-tap theme unification (#673)
chore(ci): add GitHub Actions build gate
```

---

## 7. Code style

- Kotlin style is enforced via `.editorconfig` + `ktlint` + `detekt` (see `detekt.yml`).
- In CI, **lint is currently a soft check** (does not block merge); it becomes a hard gate after
  the cleanup sprint.
- Run locally with the ktlint/detekt CLIs (see the CI workflow).

---

## 8. Label system

| Label | Meaning |
|-------|---------|
| `good first issue` | Beginner-friendly entry issue |
| `help wanted` | Open for the community to claim |
| `fork-enhancement` | Fork-specific enhancement (distinct from upstream) |

Want to claim a `help wanted` issue? Comment "I'll take it" and we'll assign you.

---

## 9. Code of conduct

Be friendly, pragmatic, and substance-first. We value verifiable improvements over grand claims.
Issues and Discussions are welcome.
