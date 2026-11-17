# Bounded collection

Before a live command, confirm the exact target and approved collection scope. Even thread
samples can pause work and contain sensitive names; do not equate filesystem read-only with
zero runtime impact. Production collection always requires explicit authorization.

- Start with existing redacted logs/metrics. Avoid VM.command_line, VM.system_properties,
  process argument listings and environment dumps unless the sensitive data is required.
- Inspect `jcmd <pid> help <command>` for that JVM's syntax and documented impact.
- Repeated Thread.print samples can reveal progress and locks; collect a bounded count with
  timestamps. Do not loop indefinitely or signal an unverified PID.
- A JFR recording requires an approved duration, settings, local output path and disk limit.
  For an approved JDK 21 target, a possible command shape is
  `jcmd <pid> JFR.start name=triage settings=default duration=60s maxsize=64m filename=<approved-local-file>`.
  Verify supported options first. Do not stop an unrelated recording. Recordings can contain
  sensitive data and maxsize does not eliminate every I/O or runtime cost.
- Histograms scan the heap at a safepoint. Some forms trigger full GC. Heap dumps can pause
  the application and require substantial disk; obtain explicit approval, check free space,
  restrict local access and state expected impact without promising a fixed pause duration.
- No collection/upload service, public Actuator dump endpoint or remote debugging listener
  is needed. Do not upload artifacts. Agree retention and deletion with the evidence owner.

If attach tools are unavailable or denied, report the limitation. Do not install privileged
agents, change container permissions or restart the service to work around it.
