# ADR 0003：Agent Runtime v2 —— Max 模式的地基

- 状态：已接受（设计），M1/M3 落地
- 日期：2026-09-29

## 背景

原版 Max 模式的天花板（均有代码证据）：

- 单线剧本循环：无计划、无验证、无反思（`ChatEngine.kt:83-232`）。
- 历史只增不减：每轮重发全部 transcript + 全部工具 schema；仅 6000 字截断（`:277`）。
- 运行状态活在内存：进程死亡即丢，无法后台执行。
- 感知是字符串：无障碍树的结构化信息未暴露给模型。
- 熔断粗放（按工具名 3 次，`:242`）；无 token/时间/动作预算；无自主安全策略。
- 无评测手段，能力提升无法度量。

## 决策

Max 不再只是"免确认 + 50 轮"，而是持久化 Agent Runtime：

1. **Durable Run**：`Run/Step/ToolCall/Observation/Artifact` 落 Room + 事件日志；前台服务执行长任务；支持恢复/暂停/接管。
2. **显式阶段机**：TASK → PLAN → ACT → OBSERVE → VERIFY →（REPLAN / DONE）；计划是一等工件。
3. **上下文工程**：分层上下文 + 按阶段裁剪工具 schema + 步骤摘要 + per-run/per-step 预算。
4. **感知 v2**：无障碍树优先，OCR/视觉兜底，操作前后 UI diff 验证。
5. **工具契约 v2**：强类型 `ToolResult`、进度、取消、元数据（幂等/危险级/并行安全）、按参数指纹熔断。
6. **子代理**：`spawn_agent` 独立上下文与预算、结构化报告、取消树。
7. **MCP**：远程 Streamable HTTP 优先，工具并入统一注册表。
8. **评测 harness**：mock 供应商 + 模拟设备 + 场景判分（v1 必须项，先于 M4）。
9. **审计**：全量动作日志；红线拦截（支付/卸载/权限变更/恢复出厂）。完整策略中心 v1.1。

## 后果

- `:core:runtime` 成为系统核心，`ChatEngine` 式单循环被替代。
- Tasks 运行中心是新 IA 的一等公民，UI 与 Runtime 同步设计。
- 评测 harness 提前到 M1，能力提升以指标说话。
