# Agent response evaluations

These cases check version selection, contract preservation and implementation reasoning in
Claude Code and Codex. They are response-level evaluations, not autonomous code-generation
benchmarks or proof of deployment readiness. The runner supplies the catalog plus relevant
reference content, so it measures selection reasoning and instruction adherence, not the
clients' automatic skill discovery or marketplace installation.

Install the validator requirements, install/authenticate the agent CLI, then run:

```sh
python3 scripts/evaluate-skills.py --check
python3 scripts/evaluate-skills.py --agent codex --case first-party-jwt
python3 scripts/evaluate-skills.py --agent claude --case first-party-jwt
```

Omit --case to collect every case; use --model to compare specific model versions.
Runs can consume the account's normal model usage. The runner works from a temporary directory,
uses read-only Codex execution / disabled Claude tools, and records the prompt, response, CLI
version, model selection, git revision and dirty-worktree status. It does not commit outputs.

Review each saved JSON transcript against its criteria. Mark each criterion pass/fail with a
quoted response excerpt; leave uncertainty explicit. A successful CLI exit means a response was
collected, not that the case passed. Compare both agents on the same clean revision and rerun
after relevant skill changes. Real generated changes still need the executable verification
suite and project-specific tests.

No unattended model calls run in CI. CI validates case structure; model evaluation requires an
authenticated CLI. No model benchmark score is claimed without reviewed transcripts.

Security scenarios cover object authorization, SBOM interpretation, AI tool/memory boundaries,
audit commit/failure behavior, and offline incident handling in both Boot trees. They use
synthetic inputs and do not authorize active scans, private-data uploads or production changes.
Their criteria are intended for separate Claude Code and Codex response reviews; JSON validation
alone is not a passing agent evaluation.

Cases may declare `references`: paths relative to their Boot version's skill tree, inside
one of the selected skills. Use these for relevant SQL, configuration or good/bad examples
that the tool-free evaluator otherwise cannot open. Selected SKILL.md files and Java
templates are included automatically; explicit duplicates are included only once.
The optimistic-lock retry, success-audit and durable-domain-event cases cover transaction
boundaries and contract preservation. Adding a case does not mean a model has passed it.

The async-overload, replicated-schedule and Redis-counter cases check executor boundaries,
durability, expired-lease behavior and atomic admission decisions. They are unexecuted model
evaluation protocols until transcripts are collected and reviewed. Their Java regression
tests cover narrower executable invariants, not agent behavior or production readiness.

See [the initial smoke review](SMOKE-REVIEW.md) for the limited run completed during development.

For a practical task and a baseline-versus-skills collection procedure, see the
[walkthrough](WALKTHROUGH.md). Use `--context baseline` to omit the catalog and reference
content; the default `--context skills` retains the reference-assisted evaluation.
