# Skill verification coverage

This is the scope of checked-in verification, not proof that every snippet works in every application.
Both trees have structure, frontmatter, link and packaging validation. "Selected" means only the
named templates or examples are compiled; "No" means there is no dedicated executable coverage.
CI results determine whether these checks pass for a particular commit.

Java fixtures target Boot 3.5.16 / Boot 4.1.0 and JDK 17 / 21. AI audit fixtures additionally pin
Spring AI 1.0.0 / 2.0.0 respectively. Database CI uses PostgreSQL 16.

| Skill | Shipped Java compiled | Runtime verification |
| --- | --- | --- |
| agent-qa-browser-regression | No Java shipped | Response-level cases and illustrative plans; no live browser/provider execution |
| ai-observability | Advisor and metrics, both trees | Async saturation, shutdown, writes and failure isolation |
| api-versioning | No | No |
| async-execution | ReportTasks and executor configuration, both trees | Proxy dispatch, thread-bound transaction isolation, failed futures and saturation rejection/recovery; no durable job recovery |
| configuration-properties | ClientProperties, both trees | Valid and invalid startup configuration |
| container-native-deployment | No | No |
| domain-driven-design | No | No |
| event-driven-messaging | No | No |
| flyway-migrations | SQL, not Java | Shipped migration examples, concurrent index and documented baseline setting |
| hateoas | No | No |
| hexagonal-architecture | No | No |
| http-interface-clients | CatalogHttpClient, both trees | Real stub HTTP server: transient/permanent status classification and read timeout |
| idempotency-patterns | SQL, not Java | Claim schema, contention, tenant isolation and rollback; not a payment workflow |
| java-supply-chain-security | Python helper, not Java | Offline CycloneDX inventory, malformed input, size/depth limits and CLI; no scanner/signature verification |
| layered-architecture | CreateOrderRequest, both trees | Bean Validation and HTTP validation; not the full architecture |
| mcp-server | Boot 4 OrderMcpTools | Compilation only; no protocol session tests |
| multi-module-maven | No | No |
| multi-tenancy | Shared-schema templates, both trees | PostgreSQL reads/writes, cache-key isolation, filter cleanup and pooled task propagation |
| null-safety | No | No |
| oauth2-resource-server | Config, extractor and identity, both trees | Signed-token issuer/audience/expiry rejection, missing scopes and email-independent account mapping |
| openapi-first | No | No |
| problem-details-rfc9457 | Handler, both trees | Selected HTTP error contracts |
| production-observability | No | No |
| resilience-retry | CatalogRetryService, both trees | Real proxies: recovery, exhaustion, permanent errors and interruption |
| rest-api-conventions | Envelope, page DTO and handler, both trees | Pagination, HTTP errors and fixture create/retrieve |
| scheduled-tasks | StaleOrderSchedule, both trees | Disabled/configured cron registration, next-fire calculation and bounded adapter; no distributed scheduler or lease implementation |
| security-audit-logging | SecurityAuditRecord, both trees | Typed fields, identity requirements and correlation validation; no sink/outbox integration |
| spring-ai-integration | No | No provider or conversation tests |
| spring-ai-security | DocumentReadTool, both trees | Scoped in-memory reads, argument validation, concurrent call budget and output limits; no model/adapter tests |
| spring-batch | No | No |
| spring-boot-migration | No | No generated application migration tests |
| spring-cloud-gateway | No | No |
| spring-data-jpa | No | Fixture entity checks mappings, versioning and rollback; not every shipped snippet |
| spring-data-redis | Config, coalescing manager, optional lookup and atomic limiter, both trees | Redis 7.4: concurrent loads, TTL, serialization, transaction callbacks, concurrent admission and fixed-window expiry; injected outage failures and real recovered reads |
| spring-incident-response | Python helper, not Java | Local hashes, changed/non-regular file rejection and CLI; no live collection or provenance proof |
| spring-modulith | No | No |
| spring-security-jwt | Templates, both trees | Token/account rejection, authorization and real HTTP authentication |
| spring-security-review | DocumentAccess, both trees | Identity/scope rejection and scoped in-memory lookup; no HTTP chain or real SQL for this template |
| testing-pyramid | No inline examples | Equivalent HTTP workflow fixture; application fixtures and Testcontainers snippets not compiled |
| transactional-patterns | No | Fixture rollback only; no full skill-specific suite |
| webflux-reactive-patterns | No | No |

## Agent evidence

The [published smoke review](../evaluations/SMOKE-REVIEW.md) records one historical Codex
first-party-JWT response on an uncommitted tree with the configured default model.
It is not a current-commit regression run. No Claude result is published.

Before claiming validated behavior for both agents, run all configured scenarios from a clean commit,
record the model identifier, CLI version and commit, and review the rubric for each response.
Separately test actual skill installation and automatic discovery: supplying a skill explicitly
in a prompt does not demonstrate discovery. Keep transcripts free of credentials and private code.

Next coverage priorities: full client deadline/cancellation under load, Redis failover and
cluster behavior, reactive tenant propagation, then executable slice/Testcontainers examples.
These fixtures do not claim distributed-lock correctness after lease expiry, database RLS
coverage, production identity-provider integration or autonomous agent benchmark results.
