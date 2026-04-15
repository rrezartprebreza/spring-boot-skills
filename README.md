# spring-boot-skills

**production-grade AI coding agent skills for Spring Boot developers**

[![skills](https://img.shields.io/badge/skills-18-4A90D9?style=flat&labelColor=1a1a2e)](https://github.com/rrezartprebreza/spring-boot-skills/tree/main/skills)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=flat&labelColor=1a1a2e&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=flat&labelColor=1a1a2e&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Claude Code](https://img.shields.io/badge/Claude_Code-compatible-white?style=flat&labelColor=555)](https://code.claude.com)
[![MCP Java SDK](https://img.shields.io/badge/MCP_Java_SDK-compatible-white?style=flat&labelColor=555)](https://github.com/modelcontextprotocol/java-sdk)
[![GitHub Stars](https://img.shields.io/github/stars/rrezartprebreza/spring-boot-skills?style=flat&label=⭐&labelColor=555&color=white)](https://github.com/rrezartprebreza/spring-boot-skills/stargazers)

> Drop a skill into your project. Your AI coding agent instantly understands your Spring Boot codebase — architecture, patterns, conventions — and codes like a senior engineer who's been on your team for years.

---

## Why this exists

AI coding agents are great at Python. They hallucinate in Spring Boot.

They generate `@Autowired` field injection instead of constructor injection. They use `ResponseEntity<?>` where you have a standard response wrapper. They ignore your existing exception hierarchy and invent a new one. They don't know your project uses Flyway, so they generate schema SQL by hand.

Skills fix this. A skill is a markdown file your agent reads before touching your code. It tells the agent **your** conventions, your stack, your gotchas — not generic Spring Boot from 2020.

This repo is a collection of battle-tested skills. Copy, adapt, drop in.

---

## 🧠 CONCEPTS

| Concept | Description |
|---------|-------------|
| [**Skills**](https://code.claude.com/docs/en/skills) | Markdown files loaded into agent context — tell the agent *how* to work in your codebase |
| [**Subagents**](https://code.claude.com/docs/en/sub-agents) | Isolated Claude instances for parallel work — use for reviews, test generation, migration tasks |
| [**CLAUDE.md**](https://code.claude.com/docs/en/memory) | Project-level persistent memory — your agent's onboarding doc |
| [**MCP Java SDK**](https://github.com/modelcontextprotocol/java-sdk) | Official Java SDK for building MCP servers — connect your Spring Boot app to any AI agent |
| [**Commands**](https://code.claude.com/docs/en/slash-commands) | Slash commands for repeatable workflows — `/generate-endpoint`, `/write-test`, `/db-migrate` |

---

## 📦 SKILLS

Drop any skill folder into `.claude/skills/` in your project. Claude Code auto-discovers them.

### 🏗️ Architecture

| Skill | Description | Tags |
|-------|-------------|------|
| [**layered-architecture**](skills/layered-architecture/) | Enforces Controller → Service → Repository separation. Prevents business logic leaking into controllers or repositories. | `architecture` |
| [**hexagonal-architecture**](skills/hexagonal-architecture/) | Ports and adapters pattern for Spring Boot. Keeps domain clean of framework dependencies. | `architecture` `ddd` |
| [**domain-driven-design**](skills/domain-driven-design/) | Aggregates, value objects, domain events in Spring Boot. Includes JPA mapping conventions. | `ddd` `jpa` |
| [**multi-module-maven**](skills/multi-module-maven/) | Parent POM conventions, shared BOM, inter-module dependency rules. Prevents circular deps. | `maven` `architecture` |

### 🔌 API Design

| Skill | Description | Tags |
|-------|-------------|------|
| [**rest-api-conventions**](skills/rest-api-conventions/) | Your project's response envelope, error codes, pagination contract, versioning strategy. Fill in the template. | `rest` `api` |
| [**openapi-first**](skills/openapi-first/) | Generate controllers and DTOs from OpenAPI spec. Uses `openapi-generator-maven-plugin`. | `openapi` `codegen` |
| [**problem-details-rfc9457**](skills/problem-details-rfc9457/) | RFC 9457 compliant error responses with Spring's `ProblemDetail`. Replaces ad-hoc error envelopes. | `error-handling` `rest` |
| [**hateoas**](skills/hateoas/) | Spring HATEOAS link building conventions. Teaches agent when and how to add hypermedia links. | `hateoas` `rest` |

### 🗄️ Data & Persistence

| Skill | Description | Tags |
|-------|-------------|------|
| [**spring-data-jpa**](skills/spring-data-jpa/) | Entity conventions, repository patterns, query naming rules, N+1 prevention, projection usage. | `jpa` `hibernate` |
| [**flyway-migrations**](skills/flyway-migrations/) | Migration naming convention, repeatable scripts, undo scripts, multi-tenant strategy. | `flyway` `migrations` |
| [**spring-data-redis**](skills/spring-data-redis/) | Cache-aside pattern, key naming, TTL strategy, serialization config. | `redis` `caching` |
| [**transactional-patterns**](skills/transactional-patterns/) | `@Transactional` propagation rules, read-only optimization, saga pattern for distributed transactions. | `transactions` |

### 🔒 Security

| Skill | Description | Tags |
|-------|-------------|------|
| [**spring-security-jwt**](skills/spring-security-jwt/) | JWT auth filter chain, token rotation, RBAC with method security. Opinionated, production-ready. | `security` `jwt` |
| [**oauth2-resource-server**](skills/oauth2-resource-server/) | OAuth2 resource server config, JWT claim extraction, scope-based authorization. | `security` `oauth2` |

### 🤖 AI & MCP

| Skill | Description | Tags |
|-------|-------------|------|
| [**spring-ai-integration**](skills/spring-ai-integration/) | Spring AI chat client, embedding, RAG pipeline patterns. Prompt template conventions. | `spring-ai` `llm` |
| [**mcp-server**](skills/mcp-server/) | Build MCP servers with the official Java SDK. Tool registration, resource exposure, prompt templates. | `mcp` `ai-agents` |
| [**ai-observability**](skills/ai-observability/) | Token usage tracking, latency monitoring, prompt/response logging for Spring AI apps. | `observability` `spring-ai` |

### 🧪 Testing

| Skill | Description | Tags |
|-------|-------------|------|
| [**testing-pyramid**](skills/testing-pyramid/) | Unit → Slice → Integration test conventions. `@WebMvcTest`, `@DataJpaTest`, Testcontainers setup. | `testing` |

---

## ⚡ QUICK START

**1. Install Claude Code** (if not already)
```bash
npm install -g @anthropic-ai/claude-code
```

**2. Drop a skill into your project**
```bash
mkdir -p .claude/skills
cp -r spring-boot-skills/skills/rest-api-conventions .claude/skills/
cp -r spring-boot-skills/skills/spring-data-jpa .claude/skills/
```

**3. Tell Claude to use it**
```
claude
> Generate a CRUD endpoint for the Order entity following our REST conventions
```

That's it. Claude reads the skill before writing a single line.

---

## 📐 SKILL ANATOMY

Every skill in this repo follows the same structure:

```
skills/rest-api-conventions/
├── SKILL.md          ← agent reads this (description + trigger conditions)
├── conventions.md    ← your actual conventions
├── examples/         ← good and bad examples
│   ├── good-response.java
│   └── bad-response.java
└── templates/        ← copy-paste starting points
    └── ResponseWrapper.java
```

**SKILL.md** has two critical parts:

```markdown
---
name: rest-api-conventions
description: >
  Use when generating REST controllers, response objects, DTOs, or error handlers.
  Defines the project's response envelope, HTTP status mapping, and error code conventions.
---

## Conventions
...
```

The `description` is a **trigger** — write it as "use when [condition]", not as a summary. This is what makes the agent actually load the skill.

---

## 💡 TIPS (from the trenches)

**Gotchas section is the most valuable** — add every time the agent does something wrong. Your future self will thank you.

**Don't describe what Spring Boot already knows.** Skills should push Claude *out of* its default behavior, not repeat the docs.

**Be opinionated about your project.** Generic Spring Boot best practices belong in a blog post. Skills belong in your `.claude/` folder.

**Fork this repo and customize.** Every team's conventions are different. These are starting points, not gospel.

**Combine with CLAUDE.md.** CLAUDE.md is for project-level memory (build commands, test runner, key architecture decisions). Skills are for domain-specific coding patterns.

| Anti-pattern | Fix |
|--------------|-----|
| Giant SKILL.md with everything | Split into focused skills, one concern each |
| "Always use constructor injection" | Already Claude's default — skip it |
| No examples | Add a `good.java` and `bad.java` — the contrast is what teaches |
| Prescriptive step-by-step instructions | Give goals and constraints, let agent decide how |
| Never updating | Add a Gotchas section, update it when agent fails |

---

## 🔥 HOT: MCP Server Skill

The [`mcp-server`](skills/mcp-server/) skill is the most powerful one here.

It teaches your agent to build production-ready MCP servers using the [official Java SDK](https://github.com/modelcontextprotocol/java-sdk) — the same protocol used by Claude, Cursor, VS Code, and every major AI coding tool.

```java
// What the agent generates with the skill loaded:
@Bean
public McpServer orderMcpServer(OrderService orderService) {
    return McpServer.sync(McpServerTransports.stdio())
        .serverInfo("order-service-mcp", "1.0.0")
        .tool(McpServerFeatures.SyncToolSpecification.builder()
            .tool(Tool.builder()
                .name("get_order")
                .description("Retrieve order by ID with full line items")
                .inputSchema(SchemaUtils.infer(GetOrderRequest.class))
                .build())
            .callHandler((exchange, args) -> {
                var req = ObjectMappers.map(args, GetOrderRequest.class);
                return new CallToolResult(orderService.findById(req.orderId()));
            })
            .build())
        .build();
}
```

Without the skill: the agent guesses the API, uses deprecated methods, or writes Python MCP code instead.

---

## 🗺️ ROADMAP

- [ ] Skills for Spring Batch
- [ ] Skills for Spring Cloud Gateway  
- [ ] Skills for Spring WebFlux / reactive patterns
- [ ] Skills for multi-tenancy
- [ ] CLAUDE.md template for Spring Boot projects
- [ ] `/generate-endpoint` command
- [ ] `/write-test` command  
- [ ] `/db-migrate` command
- [ ] Integration with [Hatch](https://github.com/rrezartprebreza/hatch) background job library
- [ ] Integration with [SpringPulse](https://github.com/rrezartprebreza/springpulse) observability

---

## 🤝 CONTRIBUTING

Skills get better with real-world use. If you find a gap — the agent did something stupid in your Spring Boot project — open a PR and add it to the Gotchas section of the relevant skill.

```
1. Fork the repo
2. Copy an existing skill as a template
3. Fill in conventions, examples, gotchas
4. PR with a one-line description of what problem it solves
```

---

## OTHER REPOS

| Repo | Description |
|------|-------------|
| [**Hatch**](https://github.com/rrezartprebreza/hatch) | Multi-module background job library for Spring Boot — REST polling, retry, Redis/JDBC backends, SSE dashboard |
| [**SpringPulse**](https://github.com/rrezartprebreza/springpulse) | Runtime observability for `@Scheduled` methods — AOP interception, WebSocket dashboard |
| [**rest-api-generator**](https://github.com/rrezartprebreza/rest-api-generator) | CLI that scaffolds Spring Boot REST APIs from plain English prompts |

---

## About

Skills for Spring Boot AI coding agents · Java 21 · Spring Boot 3.x · Claude Code · MCP Java SDK

**Topics:** `spring-boot` `java` `ai-coding-agent` `claude-code` `mcp` `skills` `spring-ai` `developer-tools` `vibe-coding` `agentic-engineering`

---

*Built by [@rrezartprebreza](https://github.com/rrezartprebreza) · Pristina, Kosovo
