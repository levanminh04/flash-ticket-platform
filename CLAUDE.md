# Claude Code entry point

This file exists only so Claude Code loads the same project rules that other agents read
from `AGENTS.md`. It adds no rule of its own.

- Source of truth for project governance: `AGENTS.md` (imported below).
- Source of truth for the governance workflow: `.agents/skills/govern-capstone-work/SKILL.md`,
  surfaced to Claude Code through `.claude/skills/govern-capstone-work` (a filesystem junction
  to the same directory, not a copy).

Edit `AGENTS.md` and `.agents/`, never this file or `.claude/`.

@AGENTS.md
