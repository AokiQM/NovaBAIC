# Copyright (C) 2026 Verlintas
# SPDX-License-Identifier: GPL-3.0-or-later
#
# This file is part of BetterAIChat2.
"""Minimal MCP Streamable-HTTP mock server for BAIC2 development.

Usage:
    python3 dev/mock-mcp-server.py [port]     # default 8766
    adb reverse tcp:8766 tcp:8766
Then add an MCP server in Library with URL http://localhost:8766/mcp
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
SESSION_ID = "mock-mcp-session"

TOOLS = [
    {
        "name": "mock_echo",
        "description": "Echo the given text back (mock MCP tool).",
        "inputSchema": {
            "type": "object",
            "properties": {"text": {"type": "string"}},
            "required": ["text"],
        },
        "annotations": {"readOnlyHint": True},
    }
]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock-mcp] " + fmt % args + "\n")

    def _respond(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Mcp-Session-Id", SESSION_ID)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path.rstrip("/") != "/mcp":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        method = request.get("method")
        rid = request.get("id")
        sys.stderr.write(f"[mock-mcp] method={method}\n")

        if method == "initialize":
            self._respond(
                {
                    "jsonrpc": "2.0",
                    "id": rid,
                    "result": {
                        "protocolVersion": "2025-03-26",
                        "capabilities": {"tools": {}},
                        "serverInfo": {"name": "mock-mcp", "version": "1.0"},
                    },
                }
            )
            return
        if method and method.startswith("notifications/"):
            self.send_response(202)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if method == "tools/list":
            self._respond({"jsonrpc": "2.0", "id": rid, "result": {"tools": TOOLS}})
            return
        if method == "tools/call":
            params = request.get("params") or {}
            name = params.get("name")
            arguments = params.get("arguments") or {}
            if name != "mock_echo":
                self._respond(
                    {
                        "jsonrpc": "2.0",
                        "id": rid,
                        "error": {"code": -32601, "message": f"unknown tool {name}"},
                    }
                )
                return
            self._respond(
                {
                    "jsonrpc": "2.0",
                    "id": rid,
                    "result": {
                        "content": [
                            {"type": "text", "text": "echo: " + str(arguments.get("text", ""))}
                        ]
                    },
                }
            )
            return
        self._respond(
            {"jsonrpc": "2.0", "id": rid, "error": {"code": -32601, "message": method}},
        )


if __name__ == "__main__":
    print(f"[mock-mcp] listening on http://127.0.0.1:{PORT}/mcp")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
