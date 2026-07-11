# 架构文档 / Architecture — ghshhf/Operit

> 面向贡献者的高层次地图。细节以源码为准；本文随代码演进而更新。
> English summary at the bottom.

---

## 1. 一句话架构

一个 **Android 端侧 AI 助手**：Compose UI 负责交互，Agent/工具调度层负责"思考与做事"，
数据层用 ObjectBox + Room + DataStore 持久化，工具执行部分跑在 **QuickJS / 原生 C++** 与
**Ubuntu 24 环境**里，模型既可走云端 API 也可走本地（MNN / llama.cpp）。

---

## 2. Gradle 模块（settings.gradle.kts）

| 模块 | 角色 | 原生？ |
|------|------|--------|
| `:app` | 主应用（Compose UI + 应用逻辑 + 原生 `app/src/main/cpp`） | ✅ (streamnative / ncnn / sherpa-ncnn) |
| `:quickjs` | QuickJS 引擎封装（工具脚本执行） | ✅ |
| `:terminal` | 终端 / Ubuntu 24 环境 | ✅ |
| `:mnn` | MNN 本地推理 | ✅ (MNN) |
| `:llama` | llama.cpp 本地推理 | ✅ (llama.cpp) |
| `:mmd` | MMD 模型渲染 | ✅ (saba / bullet3) |
| `:fbx` | FBX 模型加载 | ✅ (ufbx) |
| `:dragonbones` | DragonBones 骨骼动画 | — |
| `:showerclient` | 投屏/设备客户端 | — |

`:app` 通过 `implementation(project(...))` 依赖全部上述模块，因此 `:app:assembleDebug`
会**传递编译所有原生模块**（CI 较重，见 [BUILDING.md](BUILDING.md)）。

---

## 3. `:app` 内部分层（包结构）

命名空间 `com.ai.assistance.operit`，约 1059 个 Kotlin 文件。关键包：

| 包 | 职责 |
|----|------|
| `api/chat`, `api/speech`, `api/voice` | 云端模型 API 客户端（对话 / TTS / STT） |
| `core/application`, `core/chat`, `core/tools`, `core/workflow`, `core/config`, `core/avatar`, `core/subpack` | 领域核心：应用上下文、对话编排、**工具调度**、工作流、配置、形象、子包 |
| `data/*` | 持久化与仓储：`dao` / `db` / `repository` / `mcp` / `model` / `preferences` / `skill` / `updates` / `exporter` / `backup` / `announcement` / `api` / `collects` / `converter` |
| `integrations/externalchat`, `integrations/http`, `integrations/intent`, `integrations/tasker` | 对外集成：外部聊天、HTTP、意图、Tasker |
| `plugins/chatview`, `plugins/lifecycle`, `plugins/toolbox`, `plugins/toolpkg`, `plugins/workflow` | 插件化能力：对话视图、生命周期、工具箱、工具包、工作流 |
| `services/assistant`, `services/core`, `services/floating`, `services/notification` | 后台服务：助手、核心、悬浮窗、通知 |
| `ui/*` | Jetpack Compose UI（`ui/features` / `ui/main` / `ui/components` / `ui/theme` / `ui/floating` / `ui/permissions` / `ui/recovery` / `ui/error` / `ui/common`） |
| `util/*` | 工具函数（`markdown` / `ripgrep` / `stream` / `streamnative` / `vector` / `exceptions`） |
| `provider`, `widget` | ContentProvider / 桌面小组件 |

> **Fork 注**：差异化资产刻意放在上游极少触碰的位置——
> CI（`.github/workflows/`）、静态分析（`detekt.yml` / `.editorconfig`）、文档（`docs/` / `BUILDING.md`）、
> **回归测试（`app/src/test/`）**、开发容器（`.devcontainer/`）。这样 `sync-first` 合并上游时几乎不冲突。

---

## 4. 核心数据流（用户输入 → 工具调用 → 结果回填）

```
 用户操作 (UI / 悬浮窗 / 通知)
        │
        ▼
  ui/*  (Compose 收集输入，渲染状态)
        │
        ▼
  core/chat  (对话/Agent 编排：拼 prompt、路由模型、管理多轮)
        │
        ├─► api/chat  调用云端/本地模型 (MNN/llama) ──► 流式结果回填 UI
        │
        ├─► core/tools (统一工具调度层)
        │        │  参数 schema 校验 → 执行
        │        ├─ QuickJS 脚本 (quickjs 模块)
        │        ├─ 原生能力 (terminal / Ubuntu 24 / 文件 / 网络 / 系统)
        │        └─ 远程 MCP (data/mcp)  ↔ 外部 Skill/工具市场
        │        ▼
        │   工具结果 → 回填给 Agent → 再次推理或终态
        │
        ▼
  data/*  (记忆/历史/偏好持久化：ObjectBox + Room + DataStore)
        │
        ▼
  ui/*  渲染最终回答 / 工具输出 / 附件
```

要点：
- **工具调度是可靠性的核心**（对应上游 #657/#646/#623/#684 等积病）。本 fork 计划把分散的
  工具调用收敛到 `core/tools` 的统一调度层，加参数校验、重试、可观测日志。
- **记忆/上下文** 经 `data/repository` 读写，回归测试优先覆盖此路径。

---

## 5. 构建变体（buildTypes）

`debug` / `release` / `clone`（包名后缀 `.clone`，可共存安装）/ `nightly`（`app-nightly.apk`）。
`assembleDebug` 是 CI 的编译门禁。

---

## 6. 关键技术依赖

Jetpack Compose (BOM 2026.02.01) · Material 3 · Navigation Compose · OkHttp/SSE · jsoup ·
Retrofit/Moshi · Room · **ObjectBox** · DataStore · WorkManager · ML Kit · Glide/Coil · ExoPlayer ·
Shizuku · libsu (root) · **MCP SDK** (`io.modelcontextprotocol.sdk`) · kotlinx-serialization ·
Coroutines · security-crypto · Glance (widgets)。

---

## English summary

Single Android on-device AI assistant. Compose UI drives interaction; an Agent/tool-orchestration
layer (`core/chat`, `core/tools`) does the reasoning + acting; data layer persists via ObjectBox +
Room + DataStore; tool execution runs in **QuickJS / native C++ / an Ubuntu 24 environment**; models
are cloud APIs or local (MNN / llama.cpp).

Gradle modules: `:app` (depends on all others) plus `:quickjs :terminal :mnn :llama :mmd :fbx
:dragonbones :showerclient` — several are **native**, so `:app:assembleDebug` transitively builds
heavy C++ (see BUILDING.md). Core data flow: UI → `core/chat` (agent) → `api/chat` (model) and
`core/tools` (unified tool dispatch → QuickJS/native/MCP) → results backfilled → `data/*` persisted →
UI renders.

Fork note: differentiation assets live in files upstream rarely touches (`.github/`, `docs/`,
`app/src/test/`, `.devcontainer/`) so `sync-first` merges stay conflict-free.
