# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### 工具智能化 Tool intelligence

不堆数量，合并同类项并让工具自己多做一步——模型不再需要"查找→读坐标→点击→验证"或"先列应用再打开"。

- **UI 五合一 → `ui_control`**：按文字模糊匹配（精确/前缀/子串/编辑距离打分）、自动滚动查找（找不到会回滚位置）、点击后校验屏幕是否真的变化并回报新窗口；`type` 先检查输入焦点、可选回车提交；`press_key` 支持 enter；`scroll`/`wait_for`/`find`/`screen_text` 同在一处。移除 ui_find/ui_tap/ui_swipe/ui_type/ui_press
- **文件家族 → `files` + `file_write`**：读侧支持 list（过滤/范围）、read（offset/limit 分页并报告总长）、info，部分文件名自动解析（`report.md` 能找到 `report (1).md`）；写侧 write/append/delete。读侧保持 readOnly，Chat+ 仍可浏览。外部文件不可见时给出"设置→权限→文件访问"的可操作指引，权限中心新增"文件访问"行（Android 13+ 直达系统所有文件访问页）
- **`open_app` / `manage_app` 智能解析**：接受应用显示名（"设置"/"Settings"）、部分名或包名，内部打分匹配，启动后读取前台包名验证；manage_app 的目标同样先解析再校验，杜绝模型拼包名
- **`screen_ocr` 感知合一**：一次调用返回前台 App、窗口标题、可交互/文本元素列表（无障碍）与 OCR 全文，替代 App+OCR+find 多次往返
- **`web_read` 正文抽取 + offset 续读**：可读性提取替代纯文本倾泻，长文分页并提示下一步 offset，避免重复抓取
- **修复**：files 查询漏掉 Download/ 顶层文件（RELATIVE_PATH 精确匹配）；免手模式在进入会话时误触发一次麦克风

工具数 52 → 47。


在原项目 HOW_IT_WORKS 的基础上做了几处"不是照搬、而是升级"的改造。

### 升级 Changed

- **可回滚的上下文压缩**：压缩前把被替换的消息完整归档到 `message_snapshots`（DB 7→8 显式迁移），聊天菜单可"恢复压缩前的历史 / 放弃压缩备份"；恢复时按时间顺序重写会话，保证消息 id 始终与创建顺序一致。原项目用 memories 表存快照、"导入最近对话"手工恢复，这里改成一等公民且原子可回滚
- **web_search 管线重写**：六引擎并发扇出（DDG lite/html、Bing、Baidu、Mojeek、360）、URL 归一化与跟踪参数剥离、URL/近似标题/单域配额三层去重、标题×3+摘要×1 相关性排序、CJK 二元分词、`read_top` 元素级正文抽取（噪声过滤/相邻去重）、多 query（≤3）、5 分钟 TTL 缓存与 `refresh`、逐引擎诊断（如 `bing✓5 360✓8 ddg✗(timeout)`），失败引擎不再拖垮整体
- **OpenAI 兼容端点请求 `stream_options.include_usage`**：流式响应现在能拿到真实 prompt/completion tokens，配合 CJK 估算与 ModelCatalog 窗口，上下文计数器在"精确/估算"间自动切换
- **表格列对齐**：解析 `:---`/`:---:`/`---:` 并渲染对齐；参数自愈拒绝歧义重命名（多个候选时宁可不改）
- **压缩反馈**：对话不够长时给出本地化提示，而不是静默无操作

### 工程 Engineering

- 新增 `tools/check-strings-sync.sh`：CI（build + release）强制 zh/en 字符串键一致——原项目文档自认这项没人管
- mock 服务器：辅助请求（标题/记忆/压缩）不再被 `tooltest:` 触发词误判；web_search 参数对齐新 schema

### 新增 Added（能力补齐，均为升级版设计）

- **文档附件解析**：`docx`/`xlsx` 用零依赖 ZIP+XML 抽取（段落/共享字符串/单元格、实体解码，含单元测试）；`pdf` 在设备侧用 `PdfRenderer` 栅格化 + 现有 ML Kit OCR（扫描件也能读），最多 20 页、逐页标注。附件选择器新支持 PDF/DOCX/XLSX，实测 docx 段落与 PDF 文本均正确入库
- **Shizuku 设备 Shell**：`device:api` 新增 `ShellBridge` 抽象（Unavailable/PermissionRequired/Ready 状态流 + 受控 `exec`：超时杀进程、并发排空 stdout/stderr、输出截断）。实现封装 `IRemoteProcess`（Parcelable 包装回 `ShizukuRemoteProcess`）；`run_shell` 与 `manage_app`（list/info/force_stop/clear_data/uninstall，包名白名单校验）两个新工具；权限中心新增 Shizuku 行（状态 + 一键申请），manifest 声明 provider
- **屏幕录制**：复用已授权的 MediaProjection 会话（Android 14+ 单 VirtualDisplay 限制下切换 surface 到 MediaRecorder，录完恢复截图面），`screen_record` 工具 1–120s、视频上限 720p/4Mbps，文件落在应用私有目录
- **语音转写**：`transcribe_audio` 工具 + `SpeechInputBridge`（主线程创建/销毁识别器、超时兜底、不泄漏麦克风）
- **免手对话**：聊天菜单开关（持久化），每轮助手回复完成后自动重开麦克风并把识别结果直接发送，识别对话框打开期间与运行期间不会重入

### 测试 Tests

- 新增 SearchPipeline（8）、DocumentTextCodec（3）、ArgumentHealer 歧义用例、Markdown 表格对齐、include_usage 载荷断言；全套 127 → 140 通过

## [0.1.1] - 2026-09-29

修复 0.1.0 验收反馈的全部问题，并修复工具调用历史导致后续请求必然失败的关键缺陷。

### 修复 Fixed

- **工具协议关键修复**：助手消息带 `tool_calls` 后缺少对应的结果消息，导致真实 OpenAI 兼容端点在下一轮请求时必然报 `insufficient tool messages following tool_calls` 错误。现在三家 Provider 发请求前统一修复历史（按调用顺序补齐结果、用调用内嵌结果或错误占位兜底、丢弃孤儿 tool 消息、空白 id 生成稳定 id），旧会话自动痊愈；mock 服务器升级为同款协议校验并补充回归测试
- **上下文计数器重写**：CJK 感知的 `TokenEstimator` 估算与提供商上报值取大，展示 `12.3K/64.0K · 19%`（估算值带 `~` 前缀），70%/90% 变色预警；上下文窗口改由内置 **ModelCatalog**（约 30 个主流模型的真实窗口与默认参数）判定，精确值随每条助手消息持久化（`usageInput/usageOutput`，DB 迁移 6→7），重启不丢
- **工具调用转录修复（构造层）**：每次工具执行完成都会写入对应的 `tool` 结果消息，历史天然满足 OpenAI/Anthropic/Gemini 协议；配合发请求前的一致性修复双保险，Tasks 时间线也随之完整
- **流式滚动改为布局驱动跟随**：不再定时 animateScrollToItem（会与异步 Markdown 布局打架产生抖动），改为 `snapshotFlow(layoutInfo)` 反应式贴底 + `DragInteraction` 识别用户意图，长回复不再跳动
- **Markdown 表格与语法修复**：新增表格解析/渲染（含无外框管道表格），并对模型常见畸形语法做无损修复（全角 `｜``＊`、`###标题`、缺空格列表/引用）
- **输入框聚焦跳屏**：IME 与导航栏 inset 在根布局和输入栏重复消费导致的跳动

### 新增 Added

- **Settings 全面重做**：服务管理（编辑/设为默认/删除）、权限中心（无障碍/截屏/通知读取/使用情况/修改系统设置与运行时权限的实时状态与跳转）、外观（主题 + 8 种强调色）、语言（跟随系统 / 简体中文 / English，即时切换）、存储（占用明细与分类清理）、关于（品牌、版本、仓库、发布、反馈、开源许可）、开发者页；二级页面自动隐藏 Dock 并带滑动转场
- **服务配置向导**：粘贴 Key 自动识别服务商、8 个预设一键填充、自动拉取模型列表、按服务商推荐内置模型（标签 + 上下文窗口，点选自动套用温度/上限/深度思考默认值）、系统提示词模板四步引导
- **工具参数自愈**：双编码 JSON、散文包裹 JSON、拼写错误参数名（Levenshtein ≤2）、类型不匹配（数字/布尔字符串、单值→数组）在执行前自动修复并在结果中标注，减少无效重试
- **Tasks 运行详情**：全部/运行中/已完成/异常筛选，运行预算、任务计划、执行时间线（可展开）、删除记录、直达会话
- **强调色系统**：8 种强调色全应用即时生效；独立语言存储（`AppLocaleStore`）
- **开源许可与开发者页**

### 调整 Changed

- 左上角 Dock 精简为纯四项（Chats / Tasks / Library / Settings），进入内页自动让位
- 单元测试 103 → 127（工具历史修复、TokenEstimator、ModelCatalog、Markdown 修复与表格、参数自愈）

## [0.1.0] - 2026-09-29

BetterAIChat2 的首个公开版本 —— BetterAIChat 的继任重制（Nova）：全新架构、全新 UI、面向设备操作的 Agent Runtime。

### 新增 Added

- **流式聊天**：OpenAI 兼容 / Anthropic Claude / Google Gemini 三家适配器，SSE 流式输出、思考过程、Markdown（代码块/表格/列表/链接）、中止与重试
- **Agents**：服务商预设、一键识别 Key 前缀、拉取模型列表、每会话独立 Agent 与系统提示词
- **四种模式**：Chat（纯对话）/ Chat+（只读工具 + 计划）/ Act（逐项确认）/ Max（自主运行，预算约束）
- **48 个设备工具**：截屏、屏幕 OCR（ML Kit 中英）、无障碍 UI 自动化（查找/点击/滑动/输入/按键）、App 与系统控制、文件读写与下载、网页搜索与阅读、天气、RSS、二维码、联系人、邮件、日历、提醒、通知读取、位置、用量统计等
- **附件**：图片视觉（压缩后随请求发送）、文本文件；历史仅最近一轮携带完整附件以控制上下文开销
- **长期记忆**：自动提炼（每 10 轮）与手动触发，注入系统上下文，可在库中管理
- **上下文压缩**：摘要替换旧历史（按用户回合切边界），快照入库；已知模型窗口时 85% 自动触发
- **AI 自动标题**、对话搜索、收藏夹、Markdown 导出分享、消息复制/编辑重发/删除/朗读
- **语音**：系统语音输入与消息朗读
- **任务计划工件**：`plan_update` 工具 + 聊天置顶计划卡（步骤状态实时更新）
- **Tasks 运行中心**：运行记录（状态/模式/时长），点击直达会话
- **后台运行**：前台服务保持长任务，通知栏可停止
- **自动化引擎**：定时（每日/按周）与电量触发，动作序列无人值守执行并回报通知
- **Skills v2**：YAML recipe 技能（声明式步骤 + 指令），动态注册为工具；支持导入与「保存为技能」录制
- **子代理**：`spawn_agent` 独立上下文与预算（research 只读 / max 全量），并行发起
- **MCP 远程客户端**：Streamable HTTP，工具自动并入工具箱，库中管理服务器与连接状态
- **本地优先**：API Key 经 Android Keystore AES-GCM 加密；无云、无遥测；明文 HTTP 仅放行环回地址
- **评测 harness**：场景回放 + 判分器（`:eval`），103 个单元测试与 CI

### 修复 Fixed

- 工具执行期间卡片重复渲染（DB 消息与流式态各画一次）
- Web 工具继承 SSE 超长超时；SSRF 重定向逃逸校验
- MediaProjection 缺少必需回调注册；授权回调与服务启动竞态
- 停止运行时的工具协议不变量（未完成的调用会补写取消结果）

### 调整 Changed

- 从工具名级熔断升级：同一工具单次运行失败 3 次即拒绝并给出修正指引
- Release 构建启用 R8 + 资源收缩，APK 55MB → 46MB
- 继承 BetterAIChat 图标（自适应 + 单色 + 历史 PNG）
