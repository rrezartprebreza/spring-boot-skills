# GOOD: illustrative generation plan

Request: new Boot application using Gradle Kotlin DSL, Java 21 and a simple HTTP endpoint,
without a database. Destination is new and does not contain a repository.

Plan: inspect the approved Initializr metadata, select the requested Boot major and a stable
compatible release, request only the web/test capabilities, and stage the archive safely.
Inspect generated build scripts and wrapper, retain the selected build tool, add the endpoint
with positive/negative tests and minimal CI, then run the configured build.
Report unavailable metadata or Java as blockers; do not silently change major version.
