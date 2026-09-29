#!/usr/bin/env bash
#
# Copyright (C) 2026 Verlintas
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Verifies that every module keeps values/strings.xml (zh) and
# values-en/strings.xml (en) key sets identical. The original project admitted
# this was unchecked; here CI enforces it.
set -u

root="$(cd "$(dirname "$0")/.." && pwd)"
fail=0

while IFS= read -r zh; do
    # Brand/mode labels marked translatable="false" intentionally live in the
    # default file only and are excluded from the comparison.
    keys_zh="$(grep -o '<string name="[^"]*"[^>]*>' "$zh" | grep -v 'translatable="false"' | grep -o 'name="[^"]*"' | sort -u)"
    [ -z "$keys_zh" ] && continue
    en="${zh%/values/strings.xml}/values-en/strings.xml"
    if [ ! -f "$en" ]; then
        echo "missing English strings for $zh"
        fail=1
        continue
    fi
    keys_en="$(grep -o '<string name="[^"]*"[^>]*>' "$en" | grep -v 'translatable="false"' | grep -o 'name="[^"]*"' | sort -u)"
    missing_en="$(comm -23 <(echo "$keys_zh") <(echo "$keys_en"))"
    missing_zh="$(comm -13 <(echo "$keys_zh") <(echo "$keys_en"))"
    if [ -n "$missing_en" ]; then
        echo "keys present in $zh but missing in $en:"
        echo "$missing_en"
        fail=1
    fi
    if [ -n "$missing_zh" ]; then
        echo "keys present in $en but missing in $zh:"
        echo "$missing_zh"
        fail=1
    fi
done < <(find "$root" -path "*/src/main/res/values/strings.xml" -not -path "*/build/*" | sort)

if [ "$fail" -ne 0 ]; then
    echo "String resources are out of sync."
    exit 1
fi
echo "String resources are in sync."
