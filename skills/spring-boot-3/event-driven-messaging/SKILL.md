---
name: event-driven-messaging
description: >
  Use when implementing Kafka, RabbitMQ, Pulsar, or JMS producers and consumers in Spring Boot 3.
  Covers event contracts, idempotency, retries, dead-letter handling, ordering, and outbox delivery.
---

# Event-Driven Messaging

Design for at-least-once delivery unless the complete system proves stronger semantics.

## Event contract

- Use an immutable event envelope with `eventId`, `eventType`, `occurredAt`, `schemaVersion`, and payload.
- Treat published schemas as APIs. Add fields compatibly and never silently change meaning.
- Put correlation and causation IDs in headers or the envelope.
- Do not publish JPA entities or framework-specific serialization shapes.

## Producer rules

- Publish only after the originating state change is durable.
- Use a transactional outbox when database state and broker publication must agree.
- Use deterministic partition keys when per-aggregate ordering matters.
- Configure delivery timeout and acknowledgements explicitly.

## Consumer rules

- Make handlers idempotent with a durable processed-event record or naturally idempotent write.
- Keep the transaction boundary around the state change and idempotency marker.
- Classify transient and permanent failures before configuring retries.
- Bound retries with backoff, then route poison messages to a dead-letter destination.
- Preserve the original event, failure reason, and attempt count for replay.

## Testing

- Test serialization compatibility and handler idempotency.
- Use Testcontainers for broker integration tests.
- Test duplicate, reordered, delayed, and poison messages.

## Examples

- See `examples/good-consumer.java` and `examples/bad-consumer.java`.

## Gotchas

- Agent assumes exactly-once delivery because the broker supports transactions - end-to-end side effects still need idempotency.
- Agent retries validation failures forever - dead-letter permanent failures promptly.
- Agent publishes inside a database transaction without an outbox - a crash can split state and event delivery.
- Agent uses a random partition key - ordering for one aggregate is then lost.
- Agent deserializes directly into a JPA entity - use a versioned event contract.
