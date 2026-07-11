# 编译指南 / Building Operit (all platforms)

> 中文优先，三平台（Linux / Windows / macOS）通用步骤 + 排坑。英文见下方 *English* 段落。
> 想一键起环境？直接用仓库内置 **Dev Container**（见 `.devcontainer/`），跳过本节手动配置。

Operit 是 Android 应用（Kotlin + Jetpack Compose + QuickJS 工具执行），含多个**原生模块**
（quickjs / terminal / ncnn / sherpa-ncnn / MNN / llama.cpp / saba / bullet3 / ufbx）。
因此除了 JDK 与 Android SDK，还需要 **NDK + CMake**，以及用于工具脚本的 **Node + Python**。

---

## 1. 工具链版本（务必对齐）

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | **17** (Temurin 推荐) | `sourceCompatibility` / `JvmTarget` 均为 17 |
| Android SDK | Platform **36** + Build-Tools **36.0.0** | `compileSdk = 36` |
| NDK | **27.0.12077973** | 原生模块构建（AGP 8.13 默认 NDK） |
| CMake | **3.22.1** | 原生构建 |
| Gradle | 用仓库自带 `./gradlew`（无需单独装） | Wrapper 已锁定 |
| Node.js | **20** | web-chat / 工具脚本 |
| Python | **3.12** + `venv` | 同步脚本（`sync_example_packages.py` 等） |

> Kotlin 2.2.0 / AGP 8.13.2（见 `gradle/libs.versions.toml`）。升级 AGP 时请同步核对 NDK 默认版本。

---

## 2. 子模块（必须初始化编译相关的）

```bash
git submodule update --init --depth 1 \
  terminal \
  mnn/src/main/cpp/MNN \
  llama/third_party/llama.cpp \
  mmd/third_party/saba \
  mmd/third_party/bullet3 \
  app/src/main/cpp/thirdparty/ncnn \
  app/src/main/cpp/thirdparty/sherpa-ncnn \
  quickjs/thirdparty/quickjs \
  fbx/third_party/ufbx
```

> ⚠️ **不要**初始化 `tools/hotbuild/OperitNightlyRelease`（SSH 地址、非编译依赖）。

---

## 3. 配置 `local.properties`

仓库根目录的 `local.properties` 被 gitignore。可用 `local.properties.example` 作模板：

```properties
sdk.dir=/path/to/android-sdk        # 必须；CI 中用 ANDROID_HOME 代替
# 以下可选；缺失时 release 签名不生成、BuildConfig 字段为 "null"（debug 不受影响）
RELEASE_STORE_FILE=
RELEASE_STORE_PASSWORD=
RELEASE_KEY_ALIAS=
RELEASE_KEY_PASSWORD=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
```

---

## 4. 编译

```bash
./gradlew assembleDebug          # 生成 app-debug.apk
# 或 co-installable 克隆版：./gradlew assembleClone
# 或 nightly：./gradlew assembleNightly
./gradlew testDebugUnitTest      # 运行 JVM 单元测试（CI 同款）
./gradlew installDebug           # 装到已连接设备/模拟器
```

---

## 5. 各平台要点

### Linux (Ubuntu 22.04+)
```bash
sudo apt update && sudo apt install -y openjdk-17-jdk python3 python3-venv nodejs npm
# Android SDK：用 sdkmanager（cmdline-tools）安装 platform-36 / build-tools;36.0.0 / ndk;27.0.12077973 / cmake;3.22.1
sdkmanager --licenses
echo "sdk.dir=$ANDROID_HOME" > local.properties
```
- 行号限制/文件描述符：原生并行编译较重，建议 `org.gradle.jvmargs=-Xmx8192m`（仓库已设）。

### Windows (10/11)
- 装 **JDK 17**（Oracle/Temurin/ADOPT）。
- Android SDK：用 Android Studio 的 SDK Manager，或独立 `cmdline-tools` + `sdkmanager`。
- NDK/CMake 在 SDK Manager 的 *SDK Tools* 中勾选安装（版本同上）。
- 用 **Git Bash** 或 PowerShell 跑 `./gradlew`；路径避免中文/空格（建议仓库放 `C:\dev\Operit`）。
- 原生构建需 **Ninja/CMake** 与长路径支持：组策略开启「长路径」或缩短父目录深度。

### macOS (12+)
```bash
brew install --cask temurin@17
brew install node python@3.12
# Android SDK：brew install --cask android-commandlinetools 或 Android Studio
sdkmanager --licenses
echo "sdk.dir=$ANDROID_HOME" > local.properties
```
- Apple Silicon：NDK/CMake 为跨平台工具链，无需 Rosetta；但部分预编译 `.so` 为 arm64，模拟器建议用 arm64 镜像。

---

## 6. 常见排坑

| 现象 | 原因 / 解决 |
|------|------------|
| `SDK location not found` | 未设 `local.properties` 的 `sdk.dir`，或 `ANDROID_HOME` 未导出。 |
| `NDK not configured` / 要求安装特定版本 | 安装的 NDK 版本与 AGP 默认不一致 → 安装 `ndk;27.0.12077973` 并导出 `ANDROID_NDK_HOME`。 |
| 原生编译 OOM / 极慢 | 原生模块多且大；调大 Gradle JVM（`-Xmx8192m`），或仅构建所需模块 `:app:assembleDebug`。 |
| `Could not determine java version` | JDK 非 17 → 切到 17。 |
| 子模块目录为空 | 忘了 `git submodule update --init`（见 §2）。 |
| `GITHUB_CLIENT_SECRET` 为 "null" | 未配 `local.properties`，debug 构建无影响。 |

---

## English

Operit is an Android app (Kotlin + Jetpack Compose + QuickJS) with **native modules**
(quickjs / terminal / ncnn / sherpa-ncnn / MNN / llama.cpp / saba / bullet3 / ufbx). Besides
JDK + Android SDK you need **NDK + CMake**, plus **Node + Python** for tooling scripts.

**Toolchain (align exactly):** JDK 17 · Android Platform 36 + Build-Tools 36.0.0 · NDK 27.0.12077973
· CMake 3.22.1 · Node 20 · Python 3.12 (venv). Use the bundled `./gradlew` (no separate Gradle).

**Submodules (init the compile-related ones):**
```bash
git submodule update --init --depth 1 terminal mnn/src/main/cpp/MNN \
  llama/third_party/llama.cpp mmd/third_party/saba mmd/third_party/bullet3 \
  app/src/main/cpp/thirdparty/ncnn app/src/main/cpp/thirdparty/sherpa-ncnn \
  quickjs/thirdparty/quickjs fbx/third_party/ufbx
```
⚠️ Do **not** init `tools/hotbuild/OperitNightlyRelease` (SSH URL, not a compile dependency).

**Build:**
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # optional GITHUB_CLIENT_ID/SECRET
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
Tips: set `ANDROID_NDK_HOME` to the installed NDK; raise Gradle JVM (`-Xmx8192m`) for the heavy
native build; on Windows keep the path ASCII and short. See the troubleshooting table above.
