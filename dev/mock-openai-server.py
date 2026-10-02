# Copyright (C) 2026 Verlintas
# SPDX-License-Identifier: GPL-3.0-or-later
#
# This file is part of BetterAIChat2.

#!/usr/bin/env python3
"""Local OpenAI-compatible mock server for BAIC2 development.

Supports three scripted behaviors, all streamed as SSE:
  * plain chat replies (Markdown showcase)
  * auxiliary tasks (titles / memory extraction / summaries)
  * tool-call rounds: send "[tooltest:TOOLNAME]" and the server emits a
    tool_call, then answers from the tool result it receives next round.

Usage:
    python3 dev/mock-openai-server.py [port]      # default 8765
    adb reverse tcp:8765 tcp:8765                 # expose it to the emulator
Then configure an agent with Base URL http://localhost:8765/v1 and any key.
"""

import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
REASONING = "让我想想……这是在验证流式管线：先输出思考内容，再输出正文。"
# Trigger aliases whose request fires a different (real) tool or action.
TOOL_ALIASES = {
    "ui_control_find": ("ui_control", {"action": "find", "text": "Back"}),
    "files_list": ("files", {"action": "list", "scope": "downloads"}),
    "files_read": ("files", {"action": "read", "name": "baic2-test.md"}),
    "files_info": ("files", {"action": "info", "name": "baic2-test.md"}),
    "memory_search_spread": ("memory_search", {"query": "流式管线", "scope": "notes", "limit": 5}),
    "memory_search_user": (
        "memory_search",
        {"query": "status", "scope": "messages", "role": "user", "limit": 5},
    ),
    "memory_search_entity": ("memory_search", {"entity": "记忆工具", "limit": 5}),
    "memory_search_entity2": ("memory_search", {"entity": "隐私", "limit": 5}),
    "memory_write_note2": (
        "memory_write",
        {
            "content": "用户偏好云端同步关闭",
            "kind": "preference",
            "importance": 4,
            "entities": ["隐私"],
        },
    ),
    "memory_write_note3": (
        "memory_write",
        {
            "content": "用户每周三晚上去游泳",
            "kind": "fact",
            "importance": 3,
            "entities": ["游泳"],
        },
    ),
    "memory_write_fix7": (
        "memory_write",
        {"content": "周五前把季度报告发给团队（已更新）", "replaces": 7},
    ),
    "memory_write_merge": (
        "memory_write",
        {"content": "用户正在测试新的记忆工具功能", "kind": "fact", "importance": 3},
    ),
}

TOOL_ARGS = {
    "get_time": {},
    "device_info": {},
    "network_status": {},
    "compute": {"expression": "(12+5)*3"},
    "open_app": {"name": "Settings"},
    "take_screenshot": {},
    "screen_ocr": {},
    "web_search": {"query": "BAIC2 Android AI agent", "max_results": 4, "read_top": 1},
    "web_read": {"url": "https://example.com", "max_chars": 500},
    "spawn_agent": {"task": "汇报当前设备与时间信息", "mode": "research"},
    "mock_echo": {"text": "hi from baic2"},
    "load_skill": {"id": "status-report"},
    "status_report": {},
    "automation": {
        "action": "create",
        "name": "夜间静音",
        "trigger": "time",
        "time": "22:00",
        "actions": [{"tool": "set_volume", "args": {"level": 0}}],
    },
    "get_weather": {"city": "Beijing"},
    "get_screen_state": {},
    "generate_qr": {"text": "https://github.com/Verlintas"},
    "file_write": {"action": "write", "name": "baic2-test.md", "content": "hello from BAIC2"},
    "get_foreground_app": {},
    "transcribe_audio": {"seconds": 5},
    "screen_record": {"seconds": 5},
    "run_shell": {"command": "id"},
    "manage_app": {"action": "list", "keyword": "baic2"},
    "plan_update": {
        "steps": [
            {"title": "打开设置页", "status": "doing"},
            {"title": "确认设置页已打开", "status": "pending"},
            {"title": "汇报结果", "status": "pending"},
        ]
    },
    "memory_search": {"query": "BAIC2", "scope": "all", "limit": 5},
    "memory_read": {"conversation_id": 14, "count": 6},
    "memory_write": {
        "content": "用户正在测试新的记忆工具",
        "kind": "fact",
        "importance": 3,
        "entities": ["记忆工具"],
    },
    "memory_hold": {
        "content": "用户正在测试新的记忆工具",
        "reason": "smoke test: never record this",
    },
    "core_memory_update": {"slot": "context", "set": "正在测试记忆交互能力（0.1.18）"},
    "memory_overview": {},
    "memory_forget": {"query": "新的记忆工具"},
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep the terminal readable
        sys.stderr.write("[mock] " + fmt % args + "\n")

    def do_GET(self):
        # /v1/models requires a real-looking key, like the real APIs: this
        # catches clients that send placeholder credentials.
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or auth.removeprefix("Bearer ").strip() in ("", "placeholder"):
            body = json.dumps(
                {"error": {"message": "Invalid API key", "type": "invalid_request_error"}},
            )
            self.send_response(401)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body.encode())))
            self.end_headers()
            self.wfile.write(body.encode())
            return
        self._do_get_models()

    def _do_get_models(self):
        if self.path.rstrip("/") == "/v1/models":
            body = json.dumps(
                {"object": "list", "data": [{"id": "mock-model"}, {"id": "mock-model-pro"}]}
            ).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_error(404)

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/chat/completions":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length) or b"{}")
        messages = payload.get("messages", [])
        user_text = ""
        system_parts = []
        last_user_index = max(
            (index for index, message in enumerate(messages) if message.get("role") == "user"),
            default=-1,
        )
        # Only results produced after the last user turn count; older tool
        # output must not satisfy a new tooltest trigger.
        tool_results = [
            message.get("content") or ""
            for message in messages[last_user_index + 1:]
            if message.get("role") == "tool"
        ]
        for message in messages:
            if message.get("role") == "system":
                system_parts.append(message.get("content") or "")
            if message.get("role") == "user" and isinstance(message.get("content"), str):
                user_text = message.get("content") or ""
        system_text = system_parts[0] if system_parts else ""

        sys.stderr.write(f"[mock] system={system_text[:60]!r} count={len(system_parts)}\n")
        sys.stderr.write(
            "[mock] memory-block=%s plan-note=%s\n"
            % (
                "YES" if "What you already remember" in system_text else "no",
                "YES" if "周五前" in system_text else "no",
            )
        )
        if len(system_parts) > 1:
            sys.stderr.write(f"[mock] extra-system={system_parts[1][:120]!r}\n")
        for message in payload.get("messages", []):
            content = message.get("content")
            if isinstance(content, list):
                kinds = [part.get("type") for part in content if isinstance(part, dict)]
                sys.stderr.write(f"[mock] parts={kinds}\n")

        schema_error = validate_tool_schemas(payload.get("tools"))
        if schema_error is not None:
            body = json.dumps(
                {
                    "error": {
                        "message": schema_error,
                        "type": "invalid_request_error",
                    },
                },
            )
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body.encode())))
            self.end_headers()
            self.wfile.write(body.encode())
            return

        if not validate_tool_history(payload.get("messages", [])):
            body = json.dumps(
                {
                    "error": {
                        "message": (
                            "An assistant message with 'tool_calls' must be followed by tool "
                            "messages responding to each 'tool_call_id'. (insufficient tool "
                            "messages following tool_calls message)"
                        ),
                        "type": "invalid_request_error",
                    },
                },
            )
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body.encode())))
            self.end_headers()
            self.wfile.write(body.encode())
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def send(data, delay=0.03):
            self.wfile.write(f"data: {json.dumps(data, ensure_ascii=False)}\n\n".encode())
            self.wfile.flush()
            time.sleep(delay)

        def send_usage():
            # Mimic stream_options.include_usage: a final chunk with usage only.
            send(
                {
                    "choices": [],
                    "usage": {
                        "prompt_tokens": 123,
                        "completion_tokens": 45,
                        "total_tokens": 168,
                    },
                },
            )

        auxiliary = (
            "Summarize the conversation" in system_text
            or "short conversation titles" in system_text
            or "memory curator" in system_text
        )
        trigger = None if auxiliary else re.search(r"tooltest:([a-z_0-9]+)", user_text)
        if not auxiliary and trigger is None and not tool_results and (
            "设备" in user_text or "status" in user_text.lower()
        ):
            trigger = re.match(r"(?P<name>device_info)", "device_info")
        if trigger and trigger.group(1) == "auto":
            if not tool_results:
                send(
                    {
                        "choices": [
                            {
                                "delta": {
                                    "tool_calls": [
                                        {
                                            "index": 0,
                                            "id": "call_mock_1",
                                            "type": "function",
                                            "function": {
                                                "name": "ui_control",
                                                "arguments": json.dumps({"action": "find", "text": "Back"}),
                                            },
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                )
                send({"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
            elif len(tool_results) == 1:
                match = re.search(r"@ \((\d+), (\d+)\)", tool_results[0])
                if match:
                    coords = {"x": int(match.group(1)), "y": int(match.group(2))}
                    send(
                        {
                            "choices": [
                                {
                                    "delta": {
                                        "tool_calls": [
                                            {
                                                "index": 0,
                                                "id": "call_mock_2",
                                                "type": "function",
                                                "function": {
                                                    "name": "ui_control",
                                                    "arguments": json.dumps({**coords, "action": "tap"}),
                                                },
                                            }
                                        ]
                                    }
                                }
                            ]
                        }
                    )
                    send({"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
                else:
                    for piece in _chunks(f"无法从查找结果解析坐标：{tool_results[0]}", 8):
                        send({"choices": [{"delta": {"content": piece}}]})
            else:
                text = (
                    "自动操作完成：我找到了目标元素并点击了它。\n\n"
                    f"最后一次工具返回：{tool_results[-1]}"
                )
                for piece in _chunks(text, 6):
                    send({"choices": [{"delta": {"content": piece}}]})
            send_usage()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            return

        if trigger and not tool_results:
            name = trigger.group(1)
            tool_name = name
            args = TOOL_ARGS.get(name)
            if args is None and name in TOOL_ALIASES:
                tool_name, args = TOOL_ALIASES[name]
            if args is None:
                text = f"未知测试工具 {name}，可选：{', '.join(TOOL_ARGS)}"
                for piece in _chunks(text, 8):
                    send({"choices": [{"delta": {"content": piece}}]})
            else:
                send(
                    {
                        "choices": [
                            {
                                "delta": {
                                    "tool_calls": [
                                        {
                                            "index": 0,
                                            "id": "call_mock_1",
                                            "type": "function",
                                            "function": {
                                                "name": tool_name,
                                                "arguments": json.dumps(args, ensure_ascii=False),
                                            },
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                )
                send({"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
            send_usage()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            return

        answer = None
        if trigger and tool_results:
            answer = f"工具执行完成，返回结果是：\n\n> {tool_results[-1]}"
        if answer is None:
            answer = self._aux_answer(system_text)
        if answer is None and "table" in user_text.lower():
            answer = (
                "这是表格测试：\n\n"
                "| 名称 | 价格 | 备注 |\n"
                "| --- | --- | --- |\n"
                "| 苹果 | 3.5 | 红富士 |\n"
                "| 香蕉 | 2 | 进口 |\n\n"
                "表格结束。"
            )
        if answer is None:
            answer = (
                f"你好，我是 BAIC2 本地 mock 服务器。\n\n"
                f"我收到了你的消息：「{user_text}」\n\n"
                f"这是一个 **Markdown** 测试：\n"
                f"- 列表项一\n"
                f"- 列表项二\n\n"
                f"```kotlin\nval nova = \"streaming works\"\n```\n"
                f"结束语：管线一切正常。"
            )

        for piece in _chunks(REASONING, 4):
            send({"choices": [{"delta": {"reasoning_content": piece}}]})

        for piece in _chunks(answer, 3):
            send({"choices": [{"delta": {"content": piece}}]})
        send_usage()

        send(
            {
                "choices": [{"delta": {}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 42, "completion_tokens": 128},
            }
        )
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()

    @staticmethod
    def _aux_answer(system_text):
        """Deterministic replies for title / memory / compression requests."""
        if "short conversation titles" in system_text:
            return "量子纠缠的一句话解释"
        if "memory curator" in system_text:
            return (
                '{"remember":[{"kind":"preference","content":"用户喜欢用深色主题做演示",'
                '"importance":3,"entities":["深色主题"]}],"rehearse_keep":[2],'
                '"core_context":"正在测试新的仿生记忆系统"}'
            )
        if "Summarize the conversation" in system_text:
            return "此前对话：用户询问了量子纠缠，并对 BAIC2 的流式管线做了验证。"
        return None


def validate_tool_schemas(tools):
    """Mimic OpenAI: every function schema must be a typed object schema."""
    for tool in tools or []:
        function = tool.get("function") or {}
        parameters = function.get("parameters")
        if not isinstance(parameters, dict) or parameters.get("type") != "object":
            return (
                "Invalid schema for function '%s': schema must be a JSON Schema of "
                "'type: \"object\"', got 'type: %s'."
                % (function.get("name"), None if parameters is None else parameters.get("type"))
            )
    return None


def validate_tool_history(messages):
    """Mimic OpenAI: every tool_call must be answered before the next non-tool message."""
    index = 0
    while index < len(messages):
        message = messages[index]
        if message.get("role") == "tool":
            return False
        if message.get("role") == "assistant" and message.get("tool_calls"):
            expected = [call.get("id") for call in message["tool_calls"]]
            seen = set()
            cursor = index + 1
            while cursor < len(messages) and messages[cursor].get("role") == "tool":
                call_id = messages[cursor].get("tool_call_id")
                if call_id in expected and call_id not in seen:
                    seen.add(call_id)
                cursor += 1
            if set(expected) != seen:
                return False
            index = cursor
            continue
        index += 1
    return True


def _chunks(text, size):
    return [text[i : i + size] for i in range(0, len(text), size)]


if __name__ == "__main__":
    print(f"[mock] listening on http://127.0.0.1:{PORT}/v1 (Ctrl+C to stop)")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
