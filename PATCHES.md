# 补丁索引 / Patches Index

本 fork 在**上游源码文件**上所做的最小修改清单。每次 **sync-first** 合并上游后，据此快速重验，
避免我们的修复被上游更新悄悄覆盖。

字段：**Issue** · **文件** · **Commit** · **重验用例** · **状态**

| # | 文件 | Commit | 重验用例 | 状态 |
|---|------|--------|---------|------|
| #657 | `app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt` | `f5e30d4`（已合 main） | `ToolExecutionManagerTest.kt`（params 字面量引号容错） | ✅ 已合 main |
| #709 | 19 个 UI 文件（`.../ui/features/chat/components/ChatScrollNavigator.kt` 等，LazyListState 持久化） | `930aec5`（分支） | 编译通过 + 滚动位置不重置回归观察 | 🔶 分支中（PR #714 OPEN） |

## 管理原则（sync-first 下保持独特性）

1. **小且隔离**：每个补丁只动必要文件，降低与上游冲突概率。
2. **upstream-first**：修上游 bug 时同时向上游提 PR；合并后该差异即从本分叉消失。
3. **同步后重验**：每次 `gh repo sync` 后，对照本表逐项重跑「重验用例」；CI 也会自动跑测试拦截回归。
4. **双轨版本号**：发版记为 `v1.12.0-fork.N`，changelog 区分 upstream sync 与 our additions（见 `CHANGELOG.md`）。

> 本 fork 的差异化资产（CI / 文档 / 测试 / 开发容器）位于上游几乎不触碰的文件
> （`.github/` · `docs/` · `app/src/test/` · `.devcontainer/`），天然不易在 sync 时被覆盖。
