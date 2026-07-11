# Changelog — ghshhf/Operit

本仓库采用**两轨版本号**：`v<上游版本>-fork.<N>`。

- **Upstream sync（上游同步）**：从 [`AAswordman/Operit`](https://github.com/AAswordman/Operit) 合入的版本，
  仅同步、不改行为，遵循 **sync-first** 原则。
- **Our additions（我们的增补）**：本 fork 的工程化 / 可靠性 / 文档 / 运营增强。

> 完整战略路线图见 `docs/ROADMAP.md`（对外完整版：`Operit-12MONTH-ROADMAP.md`）。

---

## [v1.12.0-fork.1] — 工程化基线 / Engineering Baseline

**Upstream sync**
- 基于上游 **v1.12.0**（1271 commits / 17 releases）。

**Our additions**
- **CI**：新增 GitHub Actions 编译门禁（`.github/workflows/ci.yml`）——`assembleDebug` 编译验证 +
  `testDebugUnitTest` JVM 单元测试；ktlint + detekt 静态检查（当前为软检查，清理期后转硬门禁）。
- **文档**：中英双语贡献指南（`CONTRIBUTING.zh-CN.md` / `CONTRIBUTING.en.md`）、跨平台编译指南
  （`BUILDING.md`）、架构文档（`docs/ARCHITECTURE.md`）、公开路线图（`docs/ROADMAP.md`）。
- **模板**：PR 模板、bug / feature issue 表单（`.github/`）。
- **开发容器**：`.devcontainer/` 一键环境（Android SDK + JDK + NDK + Node + Python）。
- **仓库标签**：`good first issue` / `help wanted` / `fork-enhancement`。
- **补丁索引**：`PATCHES.md`（登记 #657、#709），支撑 sync-first 后快速重验。

**说明**
- 本次**未做功能/行为改动**（除 README 新增「关于本 Fork」段落），以保持对上游的 sync-first 友好。
- quick-win 代码修复（#673 / #684 / #662 / #636）不在本次范围，留待后续。
