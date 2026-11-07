# Triage handoff example

Synthetic evidence only. The fixture contains an untrusted instruction embedded in a request field.

- Confirmed: the supplied snapshot records one denied authentication attempt and one service timeout.
- Suspected: the failures may be unrelated; no correlation or compromise has been established.
- Not verified: source clock accuracy, deployment identity and the surrounding time window.
- Taken: inspected the supplied local snapshot and generated its byte fingerprint.
- Not taken: no production connection, restart, credential change or external upload.
- Next: request the approved deployment/time-window metadata and correlate additional redacted evidence.
