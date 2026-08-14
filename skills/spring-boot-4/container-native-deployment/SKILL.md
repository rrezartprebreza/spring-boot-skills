---
name: container-native-deployment
description: >
  Use when packaging Spring Boot 4 as an OCI image, JVM container, AOT application, or GraalVM
  native executable. Covers buildpacks, layers, runtime hints, probes, security, and verification.
---

# Container and Native Deployment

Choose JVM, AOT cache, checkpoint/restore, or native from measured requirements.

## Container image

- Prefer Boot build-image tasks with Cloud Native Buildpacks for standard services.
- Preserve dependency, loader, snapshot, and application layers for cache reuse.
- Pin compatible builder/run image families and promote immutable digests.
- Run as non-root and use a read-only filesystem where possible.
- Inject secrets at runtime; never copy credentials into a build context or image layer.
- Configure graceful shutdown and platform termination timing together.

## Runtime behavior

- Verify JVM memory and CPU ergonomics under real container limits.
- Expose Actuator liveness and readiness probes separately.
- Use a startup probe for expensive initialization.
- Keep writable paths explicit and bounded.

## Native image and AOT

- Use GraalVM 25 or newer with Boot 4 native build support.
- Register narrow `RuntimeHints` for reflection, resources, serialization, JNI, or proxies.
- Respect the closed-world model: runtime bean topology and classpath cannot change.
- Review profile and conditional-bean behavior during AOT processing.
- Run native integration tests against the produced executable.

## Supply chain

- Generate an SBOM, scan the final image, and regularly rebuild on patched run images.
- Sign and attest release images where the delivery platform supports it.

## Examples

- See `examples/good-dockerfile` and `examples/bad-dockerfile`.

The good example uses Boot's `jarmode=tools` extraction in a builder stage. A normal Maven package
does not create `target/dependencies/` or `target/application/` directories by itself.

## Official sources

- Container images: https://docs.spring.io/spring-boot/reference/packaging/container-images/
- Dockerfiles and layer extraction: https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html
- Native images: https://docs.spring.io/spring-boot/reference/packaging/native-image/

## Gotchas

- Agent assumes a passing JVM suite proves native compatibility - run the actual native executable.
- Agent adds broad reflection metadata - use the smallest runtime hints possible.
- Agent bakes secrets into layers - inject them only at runtime.
- Agent makes liveness depend on remote systems - use readiness for traffic dependencies.
- Agent selects native only for fashion - benchmark startup, memory, throughput, and build time.
- Agent copies nonexistent `target/dependencies` directories - extract the packaged jar in a builder stage.
