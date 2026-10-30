---
title: 思考标签流式泄漏修复（issue #1341）
fork: https://github.com/ghshhf/Operit
branch: fix/think-tag-stream-leak
status: complete
---

# 思考标签流式泄漏修复（issue #1341）

## 原本状况

Chat Completions 路径下，框架发射 think 标签不补换行，且思考内容原样进入 XML 流拆分器，三个机制叠加导致思考泄漏进正文与上下文爆炸：

1. **位置门槛（机制 2）**：`OpenAIProvider.processContentDelta` 等处 `emitter.emitTag("</think>")` 裸发，标签不落在行首，通不过 `StreamXmlPlugin` 的 `atStartOfLine` 门槛，思考块根本不成立。同一个文件里的协议元数据发射器 `emitMetadataTag` 本来就补了换行并注释了原因，think 标签却没用。
2. **字面匹配（机制 1）**：进入思考块后 `endTagMatcher` 是纯字面匹配，模型在 reasoning 里写出字面 `</think>` 就会当场闭合思考块，后半段思考裸露进正文并被持久化。
3. **历史回传（机制 3）**：`EnhancedAIService.callFunctionModel` 硬编码 `preserveThinkInHistory = true`，被污染的历史原样回传，一轮轮放大。issue 存档数据：故障当天 46 条 assistant 消息中 86 处位置不合规裸标签（7.3%）、26 条 think/close 不配对；被 API 拒收的请求体 3,180,108 字符 ≈ 1,050,087 token，其中思考内容占 67.5%。

触发条件是模型在思考里引用标签字样——排查这个 bug 的会话本身就会密集触发，日常闲聊不会，所以表现为"以前没有、某天才出现"。

## 修改意图

两条防线加一处历史治理：思考块只由框架标签闭合；框架标签稳定落在行首；历史回传默认剥离 reasoning。

## 作用域

- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt`：4 处 `emitter.emitTag("</think>")` 改为 `emitter.emitMetadataTag("</think>")`（工具调用切换、正文接管、`closeReasoningModeIfOpen`、`[DONE]` 收尾；起始标签处已改）
- 同文件新增 `sanitizeReasoningText`：思考内容交给 emitter 之前把 `<` 替换为 `<` + 零宽空格（U+200B），字面标签不再匹配、显示无损。接入两个汇聚点：流式 `processContentDelta`（同时覆盖 Responses 路径）与非流式 `emitThinkContent`（三个调用点）
- `app/src/main/java/com/ai/assistance/operit/api/chat/enhance/EnhancedAIService.kt`：`callFunctionModel` 的 `preserveThinkInHistory` 由硬编码 `true` 改为 `false`
- 回归测试 `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/ThinkTagStreamLeakTest.kt`：7 个用例，走真实 `splitBy` 见证两条防线，另有一个用例见证修复前的泄漏病灶

不在本 PR 范围（issue 建议 3 与 5）：解析器加私有标记区分框架帧标签、工具结果批次总长上限。`GeminiProvider` 另有 5 处裸 think 标签发射，但是另一套 contentBuilder 机制，单独跟进。

## 进度

- [DONE] 框架 think 标签全部改走 emitMetadataTag（补换行，落在行首）
- [DONE] 思考内容无害化（sanitizeReasoningText，覆盖流式与非流式两条路径）
- [DONE] 历史回传默认剥离 reasoning（callFunctionModel）
- [DONE] 回归测试（解析器级 4 例 + 函数级 3 例）
- [DONE] 依照仓库约束未在本地执行构建；本机构建链缺 manual-deps，Kotlin 单测由 CI 合并后执行
