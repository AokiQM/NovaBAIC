# Changelog

All notable changes to this project are documented in this file.

## [0.1.10] - 2026-09-29

### 修复 Fixed

- **DeepSeek 适配 2026-09 新 API**：`deepseek-chat`/`deepseek-reasoner` 已停服，旧配置自动路由到 `deepseek-flash`；请求按新规携带 `thinking` 开关（关推理时显式 `disabled`）、`reasoning_effort: high`，带 tools 时回传历史 `reasoning_content`，避免 400
- **GLM-5.3 禁止关闭思考**（传 `disabled` 会失败）：GLM-5.x 固定发送 `thinking.enabled`，关闭推理时改用 `reasoning_effort: low`
- **Kimi K3 固定采样参数**：K3 始终思考且固定 temperature/top_p，不再显式发送这些字段，并按官方要求回传 `reasoning_content`

### 优化 Changed

- **模型目录全量校准**（逐一核对官方文档 / 平台发布说明）：
  - OpenAI → `gpt-6-astra`、`gpt-5.6-sol/terra/luna`（均 1M 上下文）
  - Anthropic → Fable 5.1（1M）、Opus 5.5、Sonnet 5.5（1M）、Haiku 4.5（Mythos 5 受限未收录）
  - Gemini → `gemini-3.8-flash`、`gemini-3.1-pro`；Qwen → `qwen3.8-max` / `qwen3.7-plus` / `qwen3.8-flash`（均 1M）
  - Kimi → `kimi-k3`（1M）/ `kimi-k2.7-code` / `kimi-k2.6`；GLM → `glm-5.3` / `glm-5.2`（均 1M）；MiniMax → `MiniMax-M3`（1M）/ `M2.7`
  - 新条目默认开启推理标记（思考已是新一代模型默认行为）
- **预设默认模型同步**：OpenAI→`gpt-5.6-luna`、Claude→`claude-sonnet-5-5`、Kimi→`kimi-k3`、Qwen→`qwen3.7-plus`、SiliconFlow→`deepseek-ai/DeepSeek-V4-Flash`
- **上下文窗口推断表校准**：GPT-5/6、Qwen、GLM-5、Kimi K3、Fable/Sonnet 5 均为 1M，Haiku/GLM-4.x 保持保守值
- 新增 5 个 Provider 回归测试（GLM 思考常开、K3 effort 与思维回传、DeepSeek 开关等）

## [0.1.9] - 2026-09-29

采纳隐式思维链（Implicit CoT）：让分析发生在模型隐藏推理通道里，而不是把逐步推理写进可见回答——文献与实测显示可把生成 token 降低约 10–30 倍，同时准确率较显式 CoT 提升约 12–20%。

### 优化 Changed

- **全模式隐式 CoT 策略**：系统提示词统一加入 "Reasoning policy: think silently"——分析只走内部推理通道（有则用、无则静默），可见回答只给结论/动作/结果，禁止复述题目与口水话（设备实测：请求中的 system 提示词已生效）
- **三家 Provider 的推理通道对齐**：
  - OpenAI 兼容：开启深度思考且模型属于 o 系 / gpt-5 / gpt-6 时发送 `reasoning_effort: high`（其它兼容端点不发送，避免 400）
  - Gemini 2.5+：开启深度思考时发送 `thinkingConfig.includeThoughts`，思考走隐藏通道并流式回传给 App 展示
  - Anthropic：thinking 块仅回传最后一个助手回合（API 只校验该回合，历史里回传纯属浪费上下文）
- **上下文更省**：上述改动叠加后，长对话中推理模型历史不再携带冗余思维文本，配合 CoT 卡片展示（思考 N 秒/实时秒数）体验不变
- 新增 3 个 Provider 回归测试（OpenAI effort 家族判断、Anthropic 末回合 thinking、Gemini thinkingConfig）

## [0.1.8] - 2026-09-29

### 修复 Fixed

- **截屏/屏幕 OCR 一次授权只能截一次**：静态画面没有新帧时旧实现会销毁并重建 VirtualDisplay，而 Android 14+ 同一授权令牌不允许重建，于是整个投屏会话被毁。现在改为"复用最近一帧 + 首次画面轻推（resize）"，实测一次授权连续两次 `take_screenshot` + `screen_ocr` 全部成功
- **聊天页 ⋮ 菜单位置**：菜单改为锚定在按钮自身的 Box 内，弹出位置永远紧随右上角三点（实测确认）

### 新增 Added

- **CoT 体验（Astra 风格）**：思考中显示实时秒数（主题色高亮），完成后显示"思考 N 秒"并自动收起（保留一行预览）；思考耗时随消息入库（DB 12→13）
- **关于页与开发者页合并**：构建信息、运行环境（系统/设备/ABI）、复制诊断信息、更新日志、作者全部并入关于页；设置首页不再有独立开发者入口

### 优化 Changed

- **动画补齐**：全应用主题色/强调色 450ms 平滑过渡；模式芯片（Chat/Chat+/Act/Max）颜色渐变 + Codex 风格弹跳；聊天区页面进出场滑动+淡入；欢迎建议卡按压缩放反馈
- **MAX 模式能力**：预算提升至 60 轮 / 160 次工具调用 / 60 分钟；系统提示词升级为强制协议——先观察再行动、行动后复验、失败必须换招、计划实时更新、收尾逐项核对并汇报"完成/改动/未竟"

## [0.1.7] - 2026-09-29

### 修复 Fixed

- **无法获取模型列表**：三家 Provider 的 `listModels()` 直接在调用线程上执行阻塞网络请求（向导里跑在主线程）→ `NetworkOnMainThreadException` 被吞成 `request_failed`，所以怎么点都拉不到模型列表。现在全部在 `Dispatchers.IO` 上执行
- **编辑已有服务时拉取模型列表 401**：编辑态 key 输入框是有意留空的，但拉取时仍发送占位符 "placeholder" 当密钥；现在自动改用已保存（解密后）的 Key。实测：编辑 mock 服务 → 拉取到 `From API · 2 models`（mock-model / mock-model-pro）
- **Anthropic / Gemini 自定义 Base URL 带 `/v1`、`/v1beta` 后缀时路径重复**（`/v1/v1/models` → 404）：joinUrl 自动去重
- 未填 Key 时改为本地化提示"请先填写 API Key"，不再显示 `missing_credentials` 代码串

## [0.1.6] - 2026-09-29

### 修复 Fixed

- **工具模式请求必失败**：`screen_record` / `transcribe_audio` 的 parametersJson 少了一个闭合花括号（非法 JSON），导致所有带工具的模式（Chat+/Act/Max）被 provider 整单拒绝：`Invalid schema for function 'transcribe_audio' ... got 'type: null'`。已修复，并新增三层防线：
  1. 三家 Provider 的工具 schema 解析兜底失败时退回宽松对象 schema（`{"type":"object","properties":{}}`），单个坏 schema 不再毒化整个请求
  2. mock 服务器新增与 OpenAI 一致的 schema 校验（坏 schema 直接 400，回归可被设备测试捕获）
  3. 新增 `tools/check-tool-schemas.py`：CI（build + release）强制校验全部 48 个工具 schema 是合法 object schema
- 新增 2 个 provider 回归测试

## [0.1.5] - 2026-09-29

### 修复 Fixed

- **图标恢复正常比例**：自适应图标改为"完整原图等比缩放放入安全区 + 原图底色背景"，启动器不再把全幅背景按 1.5× 放大裁切；legacy 图标保持原图等比缩放

### 完善 Changed

- **CoT（思维链）全链路完善**：
  - Anthropic：解析并持久化思维签名（DB 11→12 新增 `thinkingSignature`），多轮与工具循环中原样回传 thinking 块（含签名），开启深度思考时不再发送 temperature（Anthropic 要求温度留空）
  - Gemini 2.5：解析 functionCall 的 `thoughtSignature` 并随调用回传，思考 + 工具调用不再因缺少签名失败
  - OpenAI 兼容：开启深度思考时省略 temperature（o 系 / gpt-5 等推理模型会拒绝温度参数）
  - 思维文本与签名随助手消息入库，重启后继续对话仍可正确续接

## [0.1.4] - 2026-09-29

### 修复 Fixed

- **图标改为原样应用**：用户提供的 BAIC2.png 不做任何重绘/裁剪/加底——自适应图标直接以原图为背景层，关于页与文档图标与源文件逐字节一致（同 MD5），各密度 legacy 图标仅做等比缩放；颜色与构图零改动

## [0.1.3] - 2026-09-29

新图标 + 一轮 UI 细节打磨。

### 新增 Added

- **新图标**：BAIC2 渐变三角（自适应图标前景 + 单色层、各密度方形/圆形 legacy 图标、关于页品牌图、README/docs 图标），黑底紫粉渐变

### 优化 Changed

- 思考卡片折叠时显示一行内容预览，不再是空卡片
- 代码块头部新增"复制 / 已复制"按钮（1.5s 状态反馈）
- 新建对话悬浮按钮增加投影，与列表内容拉开层次
- 关于页链接图标区分：仓库(星)/发布(刷新)/反馈(警告)/开源许可(锁)

## [0.1.2] - 2026-09-29

### 任务中心 Tasks 升级

- **失败重试**：运行详情的失败/取消任务可一键"重试"——自动回到对话并重跑该回合
- **真实消耗**：运行记录持久化"实际轮数 / 工具调用次数"（DB 9→10），预算卡显示 `2 / 50`、`1 / 120`、`2s / 30m 0s`，接近上限变色并带进度条；取消/失败也会记录已用消耗
- **影响摘要**：运行详情新增"影响摘要"，把工具调用翻译成一行行人类可读的动作（打开应用：Settings、点击：Wi-Fi、写入文件：report.md、执行命令：pm clear …），只读工具噪音自动过滤、去重限长
- **实时控制**：运行中任务在详情页可"停止运行"（跨屏幕取消，RunControlBus 驱动对话里的运行）；通知点击直达该任务的运行详情（extra open_run_id → MainActivity → Tasks）
- 纯计算层新增 ImpactSummarizer（3 测试）

- **定时任务（可调度的任务实体）**：Tasks 新增"运行记录 / 定时任务"分段。定时任务 = 名称 + 提示词 + 模式（CHAT+/MAX）+ 时间（HH:mm）+ 重复（每天或指定星期）。到点由精确闹钟（`setAlarmClock` + `USE_EXACT_ALARM`，Doze 可用）唤起前台服务，在后台完整跑一遍 Agent（工具、计划、持久化消息、运行记录、消耗统计全部照常），结果写入专属对话并推送通知；支持立即运行、启用/停用、编辑、删除；开机自动重排；升级重启后会自动把残留的"运行中"记录标记为已取消

### 修复 Fixed

- **模型选择改为"识别 API 自动拉取"**：凭据（Key/Base URL）变化即防抖自动拉取账号可用模型；拉取结果过滤掉 embedding/TTS/图像等非对话端点；未手动改过模型时自动选中"账号真实存在"的模型（优先保留同名选择，否则取列表首个），并按目录补全温度/上限/思考默认值；内置目录仅在 API 未返回列表时作为"推荐"出现；长列表支持搜索过滤与手动刷新
- **聊天页 ⋮ 菜单位置**：修复下拉菜单出现在左下角的问题（显式锚定到右上角按钮下方）
- **Tasks 只记录智能体任务**：Chat / Chat+ 的普通对话不再产生运行记录（前台服务也不为纯聊天启动）；Tasks 只列出 ACT/MAX；DB 8→9 清理历史 Chat/Chat+ 运行行

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
