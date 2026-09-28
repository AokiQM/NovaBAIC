# ADR 0004：Codex 式控制台设计语言（暗色优先）

- 状态：已接受
- 日期：2026-09-29

## 背景

原 UI 无设计系统：设置页 1928 行、模板手抄 14 遍、硬编码颜色/文案、10Hz 全表重组、无 Preview/截图测试。产品判断：原生 M3 默认观感不足以支撑"商业级"目标，视觉基准参考 Codex 一类 Agent 控制台。

## 决策

1. **设计语言**：暗色优先、信息密集、等宽日志、细线分隔、单一冷蓝强调色（`#7AA2F7`）、小圆角、功能性动效（150-250ms 弹簧）；亮色同 token 并行维护。
2. **M3 只作骨架**：交互模式、无障碍、insets、adaptive navigation 使用 material3；视觉全部走自研 token（`:core:designsystem`）。
3. **现实约束**：material3 1.4.0 稳定版中 `MaterialExpressiveTheme`/`MotionScheme`/`ExperimentalMaterial3ExpressiveApi` 仍为 internal，无法调用；因此主题用公开 `MaterialTheme`，动效自研 `Baic2Motion`（对齐 M3 Expressive 弹簧规格）。待 material3 1.5 稳定后评估迁移。
4. **信息架构**：Chats / Tasks / Library / Settings 四区。导航不做底栏，而是**左上角悬浮 Dock**：点击向下展开侧边面板（scrim + 逐项错峰入场 + 触感反馈），把整屏留给 AI 交互；平板/折叠屏的自适应形态在 M1+ 评估。
5. **纪律**：用户可见文案零硬编码（含 VM/服务/工具错误）；组件必须带 Preview；M1 起引入 Roborazzi 截图测试。

## 后果

- 视觉资产、截图测试基线、组件库都从第一天由 design system 管控。
- 与官方 M3 Expressive 组件存在一段自研期，换来无 alpha 依赖与品牌差异化。
