# 本地模型手机版（Local Model on Phone）

> 计划创建：2026-10-20 · 维护人：ghshhf · 目标分支：`dev`
>
> 目标：让 Operit 的本地推理（llama.cpp / MNN）在手机上「下载即用、跑得动、不截断」。
> 每个步骤一个最小功能单元、一个 PR；步骤编号即执行顺序。

## 背景

- llama.cpp 引擎（`llm/llama` + `LlamaProvider`）已接入聊天链路，但 GGUF 模型只能手动
  拷进 `Downloads/Operit/models/llama` 再手填文件名（MNN 有应用内市场，GGUF 没有）。
- 两个本地引擎都把 `max_tokens` 上限按显示名查找，永远命中不到，回复恒定被 native
  默认 256 token 截断（issue #447）。
- llama.cpp 构建未启用任何 GPU 后端，「GPU 层数」设置是死设置，手机 CPU 推理慢。
- 已知 open 问题：#1239（MNN int4/int8 Vulkan 崩溃）、#1341（think 标记泄漏）。

## 步骤

| # | 状态 | 分支 | 内容 | PR |
|---|------|------|------|-----|
| 1 | [DONE] | `fix/llama-max-tokens` | 修本地推理 256 token 硬截断（按 id 查找参数 + native 默认值改为按上下文派生） | #1385 |
| 2 | [DONE] | `feat/gguf-model-manager` | GGUF 模型管理：内置精选目录（ModelScope/HuggingFace 双源）+ 下载（断点续传/暂停/删除）+ SAF 导入 + GGUF 头解析器 + 单测 | 本 PR |
| 3 | [ ] | `feat/llama-vulkan-backend` | llama.cpp 构建启用 GGML_VULKAN，让「GPU 层数」真正生效 | - |
| 4 | [ ] | `feat/llama-adaptive-params` | 新配置默认值设备自适应（线程=大核数、上下文按 RAM 分档）；工厂停止硬编码 batch/ubatch/mmap/flashAttn 等，改读配置 + native 钳制；向前兼容老配置 | - |
| 5 | [ ] | `fix/mnn-vulkan-memory-mode` | 修 MNN int4/int8 量化模型 Vulkan/OpenCL 后端 memory mode 被误设 normal 导致崩溃（#1239） | - |
| 6 | [ ] | `fix/think-tag-stream-leak` | 修流式解析把字面 think 标记当思考内容剥离导致泄漏进正文与上下文爆炸（#1341） | - |

## 验收口径（每个 PR 相同）

- `python -B -m unittest discover -s ci/test -p 'test_*.py'`
- `python -B ci/script/check_repo_hygiene.py --base <merge-base> --candidate HEAD`
- `python -B ci/script/check_markdown_links.py --base <merge-base> --candidate HEAD`
- `python -B ci/script/check_localizations.py --base <merge-base> --candidate HEAD`
- Android 编译 / JVM 单测 / 打包由上游 CI 验证（AGENTS.md：默认不在本地编译）

## 备注

- 步骤 2 的下载目录与 `LlamaProvider.getModelsDir()`、`ModelListFetcher.getLlamaLocalModels()`
  扫描目录保持同一路径（`Downloads/Operit/models/llama`），下载完成即在模型列表可见。
- 目录条目的 URL 与文件大小均在上线前对上游主机实测（HTTP 200 + 精确字节数）。
