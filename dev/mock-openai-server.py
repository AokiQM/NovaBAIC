#!/usr/bin/env python3
"""Local OpenAI-compatible mock server for BAIC2 development.

Streams reasoning + text deltas as SSE so the chat pipeline can be exercised
end to end without an API key.

Usage:
    python3 dev/mock-openai-server.py [port]      # default 8765
    adb reverse tcp:8765 tcp:8765                 # expose it to the emulator
Then configure an agent with Base URL http://localhost:8765/v1 and any key.
"""

import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
REASONING = "让我想想……这是在验证流式管线：先输出思考内容，再输出正文。"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep the terminal readable
        sys.stderr.write("[mock] " + fmt % args + "\n")

    def do_GET(self):
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
        user_text = ""
        system_parts = []
        for message in payload.get("messages", []):
            if message.get("role") == "system":
                system_parts.append(message.get("content") or "")
            if message.get("role") == "user":
                user_text = message.get("content") or ""
        system_text = system_parts[0] if system_parts else ""

        answer = self._aux_answer(system_text)
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
        sys.stderr.write(f"[mock] system={system_text[:60]!r} count={len(system_parts)}\n")
        if len(system_parts) > 1:
            sys.stderr.write(f"[mock] extra-system={system_parts[1][:120]!r}\n")
        for message in payload.get("messages", []):
            content = message.get("content")
            if isinstance(content, list):
                kinds = [part.get("type") for part in content if isinstance(part, dict)]
                sys.stderr.write(f"[mock] parts={kinds}\n")

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def send(data):
            self.wfile.write(f"data: {json.dumps(data, ensure_ascii=False)}\n\n".encode())
            self.wfile.flush()
            time.sleep(0.06)

        for piece in _chunks(REASONING, 4):
            send({"choices": [{"delta": {"reasoning_content": piece}}]})

        for piece in _chunks(answer, 3):
            send({"choices": [{"delta": {"content": piece}}]})

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
        if "durable facts" in system_text:
            return '["用户喜欢深色主题", "用户正在开发 BAIC2"]'
        if "Summarize the conversation" in system_text:
            return "此前对话：用户询问了量子纠缠，并对 BAIC2 的流式管线做了验证。"
        return None


def _chunks(text, size):
    return [text[i : i + size] for i in range(0, len(text), size)]


if __name__ == "__main__":
    print(f"[mock] listening on http://127.0.0.1:{PORT}/v1 (Ctrl+C to stop)")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
