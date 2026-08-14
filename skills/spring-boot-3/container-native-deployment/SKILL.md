---
name: container-native-deployment
description: >
  Use when packaging a Spring Boot 3 application as an OCI image or GraalVM native executable.
  Covers buildpacks, layered images, JVM containers, AOT hints, probes, security, and verification.
---

# Container and Native Deployment

Choose JVM or native from measured startup, memory, throughput, and build constraints.

## Container image

- Prefer the Spring Boot build-image task with Cloud Native Buildpacks for standard services.
- Keep dependency and application layers separate to maximize cache reuse.
- Pin builder and run-image families in reproducible release pipelines.
- Run as a non-root user on a read-only filesystem where the application permits it.
- Supply secrets at runtime; never bake credentials or environment files into an image.
- Configure graceful shutdown and align platform termination grace with application timeouts.

## Runtime behavior

- Set memory and CPU limits, then verify JVM ergonomics inside those limits.
- Expose dedicated readiness and liveness probes through Actuator.
- Keep startup probes for slow initialization instead of weakening liveness.
- Write temporary files only under an explicit writable location.

## Native image

- Use Spring AOT and the supported GraalVM native build tools.
- Add `RuntimeHints` for reflection, resources, serialization, or proxies that analysis cannot infer.
- Avoid runtime bean-shape changes and other closed-world violations.
- Run native integration tests; a successful JVM test suite is not sufficient.

## Supply chain

- Generate an SBOM, scan the final image, and patch builder/run images regularly.
- Use immutable image digests for promotion between environments.

## Examples

- See `examples/good-dockerfile` and `examples/bad-dockerfile`.

The good example targets Boot 3.3+ and uses `jarmode=tools` extraction in a builder stage. For Boot
3.0-3.2, use the older `-Djarmode=layertools ... extract` command. A normal Maven package does not
create `target/dependencies/` or `target/application/` directories by itself.

## Official sources

- Container images: https://docs.spring.io/spring-boot/reference/packaging/container-images/
- Dockerfiles and layer extraction: https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html
- Boot 3.3 tools jarmode: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.3-Release-Notes#cds-support

## Gotchas

- Agent copies one fat jar into a mutable root container - use layers and a non-root runtime.
- Agent bakes secrets into image layers - inject secrets only at runtime.
- Agent adds broad reflection configuration to make native builds pass - register narrow runtime hints.
- Agent uses liveness to test every dependency - use readiness for traffic-affecting dependencies.
- Agent chooses native without measuring throughput and build cost - benchmark both deployment modes.
- Agent copies nonexistent `target/dependencies` directories - extract the packaged jar in a builder stage.
- Agent uses `jarmode=tools` on Boot 3.0-3.2 - use `layertools` until the project reaches Boot 3.3.
