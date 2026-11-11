# Spring specialists

Six task-focused agents use the existing Boot 3 and Boot 4 skills. These are native
agent definitions, not new models or replacements for skills. The per-skill
`agents/openai.yaml` files remain discovery metadata, not specialist definitions.

| Agent | Responsibility | Access intent |
| --- | --- | --- |
| `spring-architect` | Module boundaries, dependency direction, design tradeoffs | Read |
| `spring-implementer` | Scoped features and confirmed fixes | Edit and test |
| `spring-reviewer` | Correctness, auto-configuration, regressions | Read |
| `spring-security-reviewer` | Authentication, authorization, tenant and data boundaries | Read |
| `spring-test-engineer` | Contract-focused regression and integration tests | Edit tests and run |
| `spring-migration-engineer` | Explicit Boot 3 to 4 planning and migration | Read for plans; edit when requested |

Every role checks the effective Boot version and loads only relevant skills. Unresolved
versions are a prerequisite to report, not a reason to guess. Migration work needs both
variants and must distinguish their guidance. Agents do not delegate further. The parent
coordinates ownership and authorizes any external effects, commits, pushes, or deployment.

## Installation

Install the matching **skills** first using the [main installation guide](../README.md#-quick-start).
Do not copy both Boot variants into one unversioned skill folder; they share skill names.
For migrations, give the agent explicit paths to both variants in an approved local clone.

### Claude Code

The existing Boot 3 and Boot 4 Claude marketplace entries include all six agents. Refresh
the marketplace and update the installed plugin using Claude Code's plugin manager.
Check `/agents` for the plugin-scoped names, for example
`spring-boot-4-skills:spring-reviewer`. Use only the package that matches the application.

For project-local installation without a plugin, run the installer below with `--client claude`.
It writes six Markdown definitions under the target project's `.claude/agents/`.
Reload the session or use `/agents` after installation. Do not install both the plugin agents
and local copies unless you deliberately want duplicate agent definitions.

### Codex

Run the installer below with `--client codex`. It writes six standalone TOML definitions
under `.codex/agents/`. Skills installed through the existing Codex plugins remain usable;
this feature does not assume that the Codex plugin manifest installs custom agents.
Use a current Codex client supporting `name`, `description`, and `developer_instructions`
in standalone custom-agent TOML files. Restart the session after installation. Older
clients may need upgrading; this installer does not rewrite global configuration.

### Safe project installer

From this repository, use Python 3.11+ and the existing validator dependencies:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements.txt
.venv/bin/python scripts/specialist_agents.py install --client both --project /path/to/my-app --dry-run
.venv/bin/python scripts/specialist_agents.py install --client both --project /path/to/my-app
```

The target project must exist. The installer preflights every destination before writing,
refuses destination symlinks, leaves identical definitions alone, and refuses to overwrite
modified definitions. Reconcile existing files manually when upgrading. It does not install
skills, run tests, modify AGENTS.md/CLAUDE.md, enable subagents globally, or change permissions.

## Examples and coordination

```text
Use spring-architect to propose the smallest module-boundary change for this feature.
Use spring-reviewer to review this diff against main; report defects without editing files.
Use spring-security-reviewer to trace tenant access to GET /orders/{id}.
Use spring-implementer to fix the confirmed executor-selection bug in the approved files.
Use spring-test-engineer to add the regression test for that behavior.
Use spring-migration-engineer to plan a Boot 3.5 to 4.1 migration without making changes yet.
```

For Claude plugins, select the corresponding plugin-scoped name in `/agents` instead of
the local name above. Ask the parent to parallelize independent read-only reviews when useful.
Keep implementation and test edits sequential unless file ownership is explicitly disjoint.
Each subagent consumes model usage; six roles do not mean six agents must run on every task.

### Permissions are not prompts

Claude read roles expose only Read, Glob, Grep, and Skill, not Bash, Edit, or Write.
Codex read roles permit read-only shell inspection because CLI file reads and diffs may need
the shell; they prohibit edits, builds, tests, and project-script execution and inherit
the parent's permissions. **Neither these instructions nor a custom Codex agent file creates
an independent read-only sandbox.** Use the parent client's sandbox and approval controls
for enforced isolation. Skill loading is not authorization to execute commands. Run tests
through an appropriately authorized parent/test agent when a reviewer needs execution evidence.

No model is hard-coded. Claude uses `inherit`; Codex inherits its model settings. XERJ is
optional and is never installed or used to index additional data by these definitions.

## Maintenance and verification

Edit [roles.json](roles.json) and [shared.md](shared.md), then regenerate the native definitions:

```sh
python3 scripts/specialist_agents.py generate
python3 scripts/specialist_agents.py check
python3 -m unittest discover -s scripts -p 'test_*.py'
```

CI checks native syntax, generated-file drift, skill references in both versions, Claude
marketplace paths, and installer safety/idempotence. Static checks do not prove agent quality
or native-client discovery. Use the [manual smoke scenarios](SMOKE_TESTS.md) to assess those;
record actual client versions and outcomes rather than claiming unexecuted tests passed.

Official formats checked on 2026-11-11:
- [Claude custom subagents](https://code.claude.com/docs/en/sub-agents)
- [Claude plugin manifest](https://code.claude.com/docs/en/plugins-reference)
- [Codex custom agents](https://developers.openai.com/codex/multi-agent)
