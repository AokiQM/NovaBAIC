# 安全说明 Security Policy

## 报告安全问题

如果你发现了安全问题（例如密钥存储、网络传输、工具越权、Prompt 注入绕过权限门等），**请不要公开提交 Issue**。

请通过以下方式私下报告：

1. 打开仓库的 [Security 标签页](https://github.com/Verlintas/NovaBAIC/security)
2. 点击 **Report a vulnerability**，填写复现细节与影响范围

我们会在确认后尽快修复，并在发布说明中致谢（如你愿意署名）。

## 范围说明

- 本项目不收集任何用户数据，不含统计 SDK，不要求账号；所有数据留在设备本地
- API Key 使用 Android Keystore + AES-GCM 加密存储，仅发送给用户自己配置的服务商
- 模型不可信：模式门（Chat/Chat+/Act/Max）、工具危险等级、Act 确认门与权限检查在应用侧强制执行，不依赖提示词
- Web 工具带 SSRF 防护（拒绝环回/私网地址与重定向逃逸）；shell 工具仅通过 Shizuku 授权后可用
- 明文 HTTP 仅在环回地址（localhost）放行，用于本地开发；远程端点必须 HTTPS
- 远程 MCP 服务器由用户显式添加，其工具并入工具箱后与内置工具受同样的模式门与危险等级约束
