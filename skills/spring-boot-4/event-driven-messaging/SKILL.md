---
name: event-driven-messaging
description: >
  Use when implementing Kafka, RabbitMQ, Pulsar, or JMS producers and consumers in Spring Boot 4.
  Covers modular starters, event contracts, idempotency, retries, dead letters, and outbox delivery.
---

# Event-Driven Messaging

Design for at-least-once delivery unless the complete system proves stronger semantics.

## Dependencies and contracts

- Use the dedicated Boot 4 starter and matching technology test starter.
- Let Boot manage Spring Kafka, AMQP, Pulsar, and Integration versions.
- Publish immutable envelopes containing event ID, type, timestamp, schema version, and payload.
- Treat event schemas as public APIs and evolve them compatibly.
- Do not serialize JPA entities or internal Jackson configuration as contracts.

## Producer rules

- Publish only after the originating state is durable.
- Use a transactional outbox when a database write and message must agree.
- Choose a stable aggregate key when per-aggregate ordering matters.
- Configure acknowledgements, delivery timeout, and serialization failure behavior explicitly.

## Consumer rules

- Make every handler idempotent with a durable marker or naturally idempotent state transition.
- Commit the state change and idempotency marker in one transaction.
- Retry only transient failures with bounded exponential backoff.
- Route permanent or exhausted failures to a dead-letter destination with diagnostic headers.
- Build replay as an explicit operation with authorization and auditability.

## Testing

- Use the Boot 4 technology test starter and Testcontainers for broker integration.
- Test duplicate, reordered, delayed, incompatible, and poison events.
- Verify schema compatibility independently from handler tests.

## Examples

- See `examples/good-consumer.java`, `examples/good-kafka.yml`, and `examples/bad-consumer.java`.

## Official sources

- Boot 4 messaging starters: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#starters
- Spring Kafka reference: https://docs.spring.io/spring-kafka/reference/
- Spring AMQP reference: https://docs.spring.io/spring-amqp/reference/

## Gotchas

- Agent assumes broker transactions make external side effects exactly once - consumers still need idempotency.
- Agent retries every exception - classify permanent failures before retrying.
- Agent publishes directly after a repository call - use an outbox when atomicity matters.
- Agent uses old Boot 3 transitive dependencies - declare the Boot 4 messaging and test starters.
- Agent sends framework entities as events - publish a stable versioned contract.
