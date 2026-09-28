# ADR 0002：16 模块拆分与 Hilt 依赖注入

- 状态：已接受
- 日期：2026-09-29

## 背景

原项目 4 模块（app/core/providers/skills）实际边界模糊：core 依赖 Android，UI 直接强转 Application 取 AppContainer（7 处），feature 模块缺失，设置页 1928 行单文件。手工 AppContainer 在 17k 行规模下已不可维护。

## 决策

1. 拆分 16 个模块：`:app`、6 个 `:core:*`、4 个 `:feature:*`、2 个 `:device:*`、`:tools`、`:mcp`、`:eval`。
2. `:core:model` 与 `:core:engine` 为纯 Kotlin/JVM 模块，用构建边界强制领域纯净（无 Android 依赖、测试无 stub）。
3. 使用 **Hilt 2.60.1**（KSP）替代手写容器：编译期检查作用域与缺失绑定；Feature 通过 `hiltViewModel()` 取依赖。
4. 依赖方向单向向下：feature → designsystem/core；**禁止** UI 直连 DAO/Service；`:core:data` 是唯一数据入口。

## 后果

- 构建时间略增（模块拆分 + KSP），换取清晰的依赖图与可测试性。
- 后续需要约定检查（lint/detekt 规则）防止边界腐化。
- AGP 9 是 Hilt 2.60 的硬性要求，已同步升级工具链（见 ARCHITECTURE §3）。
