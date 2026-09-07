---
name: configuration-properties
description: >
  Use when introducing or correcting grouped Spring Boot configuration, typed property binding, validation, profiles or secret injection. Do not rewrite unrelated single-value configuration.
---

# Configuration Properties

## Spring Boot 3 baseline

The examples use Java 17 and Jakarta APIs supported by this Boot version. Keep dependencies managed by the project's Boot BOM.

## Bind and validate

Inspect existing property prefixes and external deployment configuration before renaming keys.
Use a typed ConfigurationProperties record for a related group of settings. Register it with
EnableConfigurationProperties or ConfigurationPropertiesScan; a record is not automatically a bean.
Use Validated and Jakarta constraints, and include spring-boot-starter-validation. Validate nested
objects with Valid. A single record constructor needs no ConstructorBinding annotation.

Use explicit units for durations and sizes. Provide defaults only when they are operationally
safe; missing credentials or required endpoints should fail startup. Do not assume NotNull
rejects zero or negative Duration values; add a duration constraint or an explicit invariant.

[ClientProperties](examples/ClientProperties.java) and
[its configuration](examples/good-client.yml) show a validated prefix with explicit duration units.
Do not turn a full API token into a record component: generated toString can expose secrets.
Resolve secrets at the integration boundary or use a redacting value type.

## Deployment and overrides

Document the public property keys and validate actual environment-variable binding in a test.
Use environment variables or a mounted secret/config tree according to the deployment platform.
Higher-precedence property sources can override packaged defaults; test the intended profile and
override path. Avoid depending on YAML list merging: lists are generally replaced.
Keep profile activation outside documents activated by that same profile.

Do not publish configprops/env actuator endpoints publicly or disable sanitization to debug.
Binding is normally at startup; changing a file or secret does not imply live refresh.
Define restart/rotation behavior when configuration changes.

## Verification

Use ApplicationContextRunner with EnableConfigurationProperties to assert valid binding and
startup failure on missing or invalid settings. Test nested validation, unit conversion and the
override path you actually deploy. Include at least one invalid duration test.

## Gotchas

- Agent defines a properties record but never registers it - enable scanning or explicit registration.
- Agent adds Validated without a validation provider - include the validation starter.
- Agent uses a bare numeric timeout - use explicit units.
- Agent adds a default credential - fail fast and inject secrets externally.
- Agent logs the full bound configuration - redact secret-bearing values.

## Official sources

- [Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Configuration property validation](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.validation)
