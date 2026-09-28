# BetterAIChat2 (BAIC2)

> 一个本地优先的 Android AI 智能体：流式聊天 + AI 真正操作你的设备。
> Nova 重制版：全新架构、全新 UI、Agent Runtime v2。原 [BetterAIChat](https://github.com/Verlintas/BetterAIChat) 仅作参考，不继承其代码与数据。

**状态：M4 进行中** — M3 全部完成（工具框架/截屏 OCR/无障碍 UI 自动化/计划工件/确认门/Tasks 运行中心/后台运行）；M4 已落地 Web/文件/媒体/设备状态工具批次（38 个工具）；92 个单元测试全绿。下一步：通知与个人数据工具、自动化引擎、Skills v2、子代理并行。

## 核心目标

1. **商业级 UI**：Codex 式 Agent 控制台设计语言（暗色优先、信息密集、等宽日志、运行面板），Material 3 仅作交互与无障碍骨架。
2. **Agent 能力上限**：Max 模式升级为持久化 Agent Runtime —— 计划-验证闭环、子代理并行、上下文工程、评测 harness、MCP 远程工具。

## 技术栈

| 项 | 版本 |
| --- | --- |
| Gradle / AGP | 9.6.1 / 9.4.1 |
| Kotlin / KSP | 2.3.21 / 2.3.12 |
| Compose BOM | 2026.09.00（material3 1.4.0） |
| DI | Hilt 2.60.1 |
| 数据 | Room 2.8.5 · DataStore |
| 网络 | OkHttp · kotlinx.serialization |
| SDK | compileSdk 37 · targetSdk 36 · minSdk 26 |

## 模块结构

```
:app                   装配、MainActivity、四区导航
:core:model            纯 Kotlin 领域模型（JVM）
:core:engine           Agent 循环、工具契约（JVM）
:core:runtime          Agent Runtime v2（持久化运行）
:core:data             Room + DataStore + 仓库
:core:network          OkHttp + SSE + Provider 适配
:core:designsystem     设计系统（暗色优先 token + 组件）
:feature:chat         聊天
:feature:conversations 会话
:feature:tasks         Agent 运行中心
:feature:settings      设置中心
:device:api / :device:impl  Android 能力接口与实现
:tools                 内置工具
:mcp                   MCP 客户端（远程优先）
:eval                  评测 harness
```

## 构建

```bash
# JDK 17 + Android SDK（platform 37, build-tools 37.0.0）
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # 单元测试（54+）
./gradlew lintDebug              # Android Lint
```

### 本地联调（无需 API Key）

```bash
python3 dev/mock-openai-server.py 8765   # OpenAI 兼容 mock（流式 + 思考 + Markdown）
adb reverse tcp:8765 tcp:8765            # 模拟器/真机访问宿主机
# App 内配置：Base URL http://localhost:8765/v1，Key 任意
```

## 文档

- [架构设计](docs/ARCHITECTURE.md)
- ADRs：[0001 独立重制](docs/adr/0001-standalone-rebuild.md) · [0002 模块化与 Hilt](docs/adr/0002-modules-and-hilt.md) · [0003 Agent Runtime v2](docs/adr/0003-agent-runtime-v2.md) · [0004 设计语言](docs/adr/0004-design-language.md)

## 里程碑

| 阶段 | 状态 |
| --- | --- |
| M0 工程骨架 | ✅ 完成 |
| M0.5 导航壳重做 | ✅ 完成 |
| M1 垂直切片（Agents + 流式聊天 + Runtime 骨架 + eval 骨架） | ✅ 完成 |
| M2 完整聊天（三家 Provider/附件/语音/记忆/压缩/搜索/收藏/导出/设置） | ✅ 完成（语音助手免提模式与 PDF/Office 解析留待后续） |
| M3 Runtime v1（工具框架 / 截屏 OCR / 无障碍 UI 自动化 / 计划工件 / 确认门 / Tasks 运行中心 / 后台运行） | ✅ 完成 |
| M4 子代理 + 工具 + 自动化 + Skills v2 | 计划中 |
| M5 MCP + 评测扩充 + CI 指标门禁 | 计划中 |
| M6 打磨与 v0.1.0 发布 | 计划中 |
