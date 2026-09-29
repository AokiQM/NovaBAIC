#!/usr/bin/env python3
# Copyright (C) 2026 Verlintas
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Every tool's parametersJson must be a JSON Schema object with type=object,
# otherwise OpenAI/Anthropic/Gemini reject the whole request ("Invalid schema
# for function ... got 'type: null'"). That exact bug shipped once; this guard
# keeps it from shipping again.
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PATTERN = re.compile(r'parametersJson\s*=\s*(?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")', re.S)

def main() -> int:
    checked = 0
    failures = []
    for path in sorted((ROOT / "tools/src/main/java").rglob("*.kt")):
        for match in PATTERN.finditer(path.read_text()):
            raw = match.group(1) if match.group(1) is not None else match.group(2)
            checked += 1
            try:
                schema = json.loads(raw)
            except Exception as error:  # noqa: BLE001
                failures.append((path, f"invalid JSON: {error}"))
                continue
            if not isinstance(schema, dict) or schema.get("type") != "object":
                failures.append((path, "missing \"type\": \"object\""))
    if failures:
        for path, why in failures:
            print(f"{path.relative_to(ROOT)}: {why}")
        print(f"{len(failures)} of {checked} tool schemas are invalid.")
        return 1
    print(f"All {checked} tool schemas are valid object schemas.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
