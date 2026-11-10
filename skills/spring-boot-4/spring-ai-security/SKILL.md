---
name: spring-ai-security
description: >
  Use when threat-modeling or hardening a Spring AI RAG, chat, or tool-calling workflow
  against prompt injection, unauthorized actions, data exposure, or unbounded execution.
  Ordinary model-provider integration alone does not require this review.
---

# Spring AI Security

## Version boundary

Target Spring Boot 4 and the project's compatible Spring AI 2.x line. Preserve the repository's managed coordinates and provider choices.
The Java 17 tool boundary here is deliberately independent of Spring AI APIs. Wire it into
the selected version's tool mechanism and verify that adapter separately.

## Establish the trust boundary

Map user input, uploaded documents, retrieved chunks, memory, tool output and rendered
model responses. All can contain attacker-controlled content. A prompt instruction such as
"ignore malicious text", a delimiter, classifier or keyword denylist is not an authorization
boundary. Record what the model can read and which effects its tools can cause.

- Derive caller and tenant from verified server-side identity, not prompts, model arguments
  or an arbitrary client header. Scope retrieval in the storage query and recheck authorization
  before returning documents; output-only filtering is too late.
- Enforce the same ACLs during ingestion, vector retrieval, document lookup and cache/memory
  reuse. Bind conversation IDs to caller/tenant authorization; randomness alone is insufficient.
- Expose narrowly typed tools, not shell execution, raw SQL, arbitrary URLs or general admin APIs.
  Validate arguments and enforce permissions in application code immediately before effects.
- Require a trusted approval flow for sensitive writes: bind approval to actor, tenant, exact
  normalized action/arguments, expiry and one-time use. Reauthorize on execution. A model-supplied
  approved=true field or stale approval after argument changes is not consent.
- Bound tool calls, model iterations, response size, retrieval size, deadlines and per-user spend.
  Carry the same budget through retries and nested calls; a new tool instance must not reset a
  request's limit. Apply network/DB timeouts and cancellation independently of call counts.
- Restrict outbound destinations and data sent to providers. Do not send credentials or
  unnecessary private content. Treat returned HTML/Markdown/URLs as untrusted when rendering.
- Audit decisions and identifiers without recording full prompts, documents, tokens or secrets.

## Tool example and verification

[DocumentReadTool](templates/DocumentReadTool.java) accepts only a document UUID.
Its caller identity is server-supplied and immutable, permissions are checked in code,
and a server-owned CallBudget is shared across every tool instance for that authenticated turn.
All invocation attempts consume it, including denied or malformed calls. Do not create a new
budget on retry or share a caller/tool singleton across users. Never expose constructors,
caller identity or budgets as tool input. The reader must enforce tenant AND account access
in its storage query. For summarizing a preselected upload, prefer no tool at all when a
server-authorized document load is sufficient.

The example is read-only. It does not implement approval for writes, a Spring AI adapter,
vector-store ACLs, model-output sanitization or network timeouts. Its result-size check runs
after loading the body: also bound storage reads/streaming and total bytes in the adapter.
The fixture tests identity
scoping, invalid arguments, missing permission, exhausted budgets and oversized results without
calling a model. It is not a measured prompt-injection detection benchmark.

Use [the good scenario](examples/good-boundary.md) and [the bad scenario](examples/bad-boundary.md)
to plan tests involving hostile retrieved text, cross-user conversation IDs, cross-tenant
documents, repeated tool calls and legitimate text containing words such as "delete".
Assert the unauthorized effect never occurs; model refusal text alone is not a pass.

## Review output

List the trust boundaries, allowed effects, demonstrated controls and remaining gaps.
Label each claim **confirmed**, **suspected**, or **not verified**; distinguish deterministic
policy tests from provider/model evaluations. Preserve redacted evidence and test versions.
Request approval before sending private fixtures to a model provider or testing live tools.

## Gotchas

- Agent trusts a system prompt to enforce permissions - authorize every tool call in application code.
- Agent lets the model choose a tenant ID - derive scope from the authenticated server context.
- Agent resets budgets on retries - share the request budget and bound nested tool execution.
- Agent calls a filtered response proof of safety - assert that forbidden effects never happened.

## Official sources

- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [OWASP prompt injection prevention](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)
- [OWASP AI agent security](https://cheatsheetseries.owasp.org/cheatsheets/AI_Agent_Security_Cheat_Sheet.html)
