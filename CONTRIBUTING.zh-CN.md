# 贡献指南（中文）· ghshhf/Operit

> 本文档是 `ghshhf/Operit` 的**工程化进阶版**贡献入口。英文版见 [CONTRIBUTING.en.md](CONTRIBUTING.en.md)；
> 编译细节见 [BUILDING.md](BUILDING.md)；架构总览见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)；
> 战略路线见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 1. 这个 Fork 是什么

`ghshhf/Operit` 是基于上游 [`AAswordman/Operit`](https://github.com/AAswordman/Operit) 的社区 fork，定位为
**「工程化进阶版」**：

> **上游负责想象，我们负责让它真的能跑。**

我们不是「更快同步的上游」，而是把 **CI 门禁、自动化测试、文档、月度发版、贡献者友好** 做成护城河的那一个分支。
判断一个改动是否「进阶版」的标准很简单：**它是否让这个版本更可靠、工具更稳、新人更好上手。**

---

## 2. sync-first 原则（必读）

我们采用 **sync-first**：上游一有更新，先 `gh repo sync` 合进我们的 `main`，再在其上做修复。

- **不要**把我们 fork 的逻辑 rebase 到上游去；保持 `main` 跟踪上游 + 我们的补丁。
- 你对**上游文件**做的任何修改，必须记录到 [`PATCHES.md`](PATCHES.md)（issue / 文件 / commit / 重验用例），
  以便每次上游同步后快速重验，避免被覆盖。
- 我们特有的增强（CI、文档、测试、运营）大多位于上游**几乎不触碰的文件**（`.github/`、`docs/`、`app/src/test/`、
  `.devcontainer/`），天然不易冲突。
- 修上游 bug 时，**同时向上游提 PR**（upstream-first）。一旦被合并，该差异即从我们分叉消失，sync 更轻松。

---

## 3. 环境准备（30 分钟跑起来）

完整步骤见 [BUILDING.md](BUILDING.md)，速览：

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17 (Temurin) | 编译 |
| Android SDK | Platform 36 / Build-Tools 36.0.0 | 编译 |
| NDK | 27.0.12077973 | 原生模块（quickjs / terminal / ncnn…） |
| CMake | 3.22.1 | 原生构建 |
| Node.js | 20 | web-chat / 工具脚本 |
| Python | 3.12 + venv | 同步脚本 |

最省事的方式：用仓库内置的 **Dev Container**（`.devcontainer/`）一键起环境，免去本地装 5 种工具链。

```bash
git clone https://github.com/ghshhf/Operit.git
cd Operit
git submodule update --init --depth 1 \
  terminal mnn/src/main/cpp/MNN llama/third_party/llama.cpp \
  mmd/third_party/saba mmd/third_party/bullet3 \
  app/src/main/cpp/thirdparty/ncnn app/src/main/cpp/thirdparty/sherpa-ncnn \
  quickjs/thirdparty/quickjs fbx/third_party/ufbx
# 配置 SDK 路径
echo "sdk.dir=$ANDROID_HOME" > local.properties   # 或用 local.properties.example
./gradlew assembleDebug
```

> ⚠️ `tools/hotbuild/OperitNightlyRelease` 子模块使用 SSH 地址且非编译依赖，**不要初始化它**。

---

## 4. 开发与提 PR 流程

1. 从 `main` 切分支：`git checkout -b fix/short-description`。
2. 编码 + **写/更新回归测试**（见第 5 节）。
3. 本地自测：`./gradlew assembleDebug testDebugUnitTest`。
4. 提交信息遵循 **Conventional Commits**（见第 6 节）。
5. 推到你的 fork 并开 PR 到 `ghshhf/Operit:main`。
6. 等待 **CI（build + lint）全绿**；填写 PR 模板，关联 issue。
7. 维护者 review → merge。

---

## 5. 测试是硬要求（不是可选项）

我们靠测试守护「不会回退」的承诺。

- **位置**：JVM 单元测试放在 `app/src/test/`（不依赖模拟器，CI 可跑）。
- **必做**：任何 bug 修复都要带来一条回归用例（把「它曾经崩」固化下来）。
- 运行：`./gradlew testDebugUnitTest`。
- 尚无 Android 设备的同学，请优先补 JVM 层测试（工具参数解析、JSON 序列化、记忆读写、MCP 编解码等）。

---

## 6. 提交信息规范（Conventional Commits）

```
<type>(<scope>): <subject>
```

常用 `type`：`feat` / `fix` / `docs` / `chore` / `refactor` / `test` / `style`。
示例：

```
fix(tools): 容错 package_proxy params 字面量引号 (#657)
feat(ui): 主题一键统一 (#673)
chore(ci): 新增 GitHub Actions 编译门禁
```

---

## 7. 代码风格

- Kotlin 风格由 `.editorconfig` + `ktlint` + `detekt` 约束（见 `detekt.yml`）。
- CI 中 **lint 当前为软检查**（不阻断合并），清理期后转为硬性门禁。
- 本地可跑：`./gradlew` 之外，可用 ktlint/detekt CLI 自检（见 CI 工作流）。

---

## 8. 标签体系

| 标签 | 含义 |
|------|------|
| `good first issue` | 适合新贡献者的入门 issue |
| `help wanted` | 欢迎社区认领 |
| `fork-enhancement` | 本 fork 特有的增强（区别于上游） |

想认领带 `help wanted` 的 issue？评论一句「我来」即可，我们会指派给你。

---

## 9. 行为准则

友好、务实、对事不对人。我们重视可验证的改进胜过宏大的声明。欢迎提 Issue / Discussion。
