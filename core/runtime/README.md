# :core:runtime — reserved seam

This module is intentionally **empty**. It is the planned home for run
orchestration (the persistent `Run → Step → ToolCall → Observation` runtime
described in `docs/ARCHITECTURE.md` §5) once it outgrows `:core:engine`.

It exists in `settings.gradle.kts` so that the module graph, Hilt setup and
docs already have the slot: moving code here later is a rename, not a
re-architecture. Nothing should depend on it yet.

If files are added, they follow the same rules as every module: GPL header
(`tools/check-license-headers.sh`), tests, and a mention in
`docs/HOW_IT_WORKS.md` §2.
