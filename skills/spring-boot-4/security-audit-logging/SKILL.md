---
name: security-audit-logging
description: >
  Use when designing or reviewing security audit events, their redaction, durable storage,
  integrity, or failure policies in Spring Boot. General metrics and debugging logs belong
  to production observability rather than this security-event workflow.
---

# Security Audit Logging

## Version boundary

Target Spring Boot 4 with Spring Framework 7 and Spring Security 7. Check the selected release's Actuator audit APIs before wiring listeners.
The shipped Java 17 record has no Boot/Jackson dependency and is shared across both trees.

## Event contract

Choose events needed for the requested security or business requirement: authentication
decisions, access denial, privilege changes, sensitive exports and administrative actions.
Separate attempted, denied, successful and failed operations. Do not log SUCCESS before
the authoritative transaction commits.

Prefer typed fields: event ID, UTC time, verified actor ID, tenant ID, action, target ID,
outcome and server-generated correlation ID. Unauthenticated events may have no actor;
do not mislabel a supplied username as verified identity. Record only identifiers needed
for the investigation, with controlled access and retention.

[SecurityAuditRecord](templates/SecurityAuditRecord.java) demonstrates bounded typed fields
and a restricted correlation identifier. It intentionally has no arbitrary message, headers,
token, request body or exception field. This is an event contract, not a durable audit sink.
Use a structured serializer, never concatenate JSON or interpolate untrusted text into lines.
The sample assumes tenant-scoped operations and UUID identities. Adapt those types and the
success invariant to the application's actual identity model; do not invent tenants for a
single-tenant system. Correlation IDs must be server-generated, not arbitrary caller text.

## Delivery and access policy

- Keep credentials, cookies, bearer tokens, passwords, session secrets, sensitive prompts,
  private documents and full configuration out of audit records. Sanitize controls and bound
  any separately approved free-text fields. Hashing arbitrary identifiers is not automatic anonymization.
- Spring Boot's in-memory audit repository is for development, not durable evidence.
  Authentication events alone do not capture every business authorization decision.
- For committed mutations requiring durable audit, use an atomic outbox alongside the business
  transaction. Deliver idempotently by event ID; handle duplicate delivery, backpressure, retention
  and retries. An after-commit listener can still lose events on process failure.
- Explicitly choose failure behavior by operation. A required audit write may need to prevent
  a privileged mutation; optional read-event loss may instead trigger bounded buffering and alerts.
  Do not globally fail every request or silently drop required audit events.
- Separate application write permissions from investigator read and retention/delete permissions.
  Define export controls, clock synchronization, access auditing and integrity checks.
  Append-only configuration alone is not proof of tamper resistance.
- Do not publish Actuator auditevents, env or configprops to solve an investigation.
  Collection and external export require approved scope and a redacted handoff.

## Verification and output

Exercise malformed/oversized correlation IDs, missing fields, all outcomes and stable event IDs.
The local fixture tests record construction, not a production logging backend.
Application integration tests must also prove rollback emits no committed-success event,
outbox retry deduplicates, and sink failures follow the selected policy.

Provide a field inventory, access/retention policy, delivery semantics, failure matrix and
tests run. Label evidence **confirmed**, **suspected**, or **not verified** rather than
claiming compliance from the mere presence of log statements.
See [good design](examples/good-audit.md) and [bad design](examples/bad-audit.md).

## Gotchas

- Agent logs request headers or principal.toString - select typed non-secret identifiers.
- Agent writes SUCCESS before commit - tie success evidence to the committed operation.
- Agent uses in-memory events as a production ledger - choose durable, access-controlled storage.
- Agent swallows sink failures - implement the operation's documented audit failure policy.

## Official sources

- [Boot auditing](https://docs.spring.io/spring-boot/reference/actuator/auditing.html)
- [OWASP logging guidance](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
