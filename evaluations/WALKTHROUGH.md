# Practical walkthrough: preserve an existing REST contract

An existing API matters more than a generic preferred convention. This example asks an agent
to extend a Spring Boot 3.5 application without changing its public contract.

## Try it in a project

Install the Boot 3 skills using the [Quick Start](../README.md#-quick-start), open a disposable
copy of a matching project, and give Claude Code or Codex this request:

```text
This Boot 3.5 MVC project returns plain success DTOs, uses Long order IDs and
/api/orders without versioning. Add paginated GET /api/orders, preserving the
existing public contract and architecture. Describe the controller and
serialization changes.
```

The relevant reference is
[`rest-api-conventions`](../skills/spring-boot-3/rest-api-conventions/SKILL.md).
Review the generated changes before running them. The expected behavior is:

| Check | What to inspect |
| --- | --- |
| Public contract | `/api/orders`, `Long` IDs and plain success DTOs remain unchanged. |
| Pagination | A stable page DTO is serialized instead of exposing framework page internals. |
| Resource bounds | Page size is capped and sortable fields are allowlisted. |
| Version compatibility | No Framework 7 native API versioning is introduced into Boot 3.5. |
| Architecture | The agent follows existing project boundaries rather than migrating the application. |

These are acceptance criteria, not evidence that an agent has already passed.

## Collect a response-level comparison

The evaluation runner can collect responses to exactly this task with and without repository
references. Install its dependencies and authenticate your chosen CLI first:

```sh
python3 -m pip install -r scripts/requirements.txt
python3 scripts/evaluate-skills.py --check
python3 scripts/evaluate-skills.py --agent codex --case boot3-existing-contract --context baseline
python3 scripts/evaluate-skills.py --agent codex --case boot3-existing-contract --context skills
```

Use the same explicit `--model MODEL_ID` on both commands for a reproducible comparison.
Replace `codex` with `claude` to use that CLI. Runs consume normal model usage.

The baseline receives shared instructions and the user request only. The skills condition
also receives the catalog and selected references. Both run in a fresh temporary directory;
no existing application source is supplied. CLI-global configuration may still affect the
responses. This measures reference-assisted reasoning, not installation discovery or a working
application. The runner records the condition, prompt, response, CLI version and repository revision
in `evaluations/results/`; it does not automatically score responses.

Review both responses against the same implementation criteria, recording a short supporting
quote for each pass or failure. Skill-path selection criteria in other cases are routing checks;
report them separately rather than counting them against a baseline without a catalog.
Leave ambiguous criteria unscored. Repeat across cases and runs before claiming an advantage;
a single pair is a demonstration, not a reliable performance estimate.

## Verify implementation separately

Use the [executable verification suite](../verification/README.md) for shipped-template behavior,
and add HTTP and persistence tests in the actual target application for generated changes.
Passing repository validation alone does not show improved model output or production readiness.
No measured with/without-skills result is claimed by this walkthrough.
