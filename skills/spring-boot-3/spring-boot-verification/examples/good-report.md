# GOOD: synthetic readiness report

This is illustrative evidence, not an executed repository run.

Scope: local checkout at a recorded commit; dirty files listed; Boot/JDK/wrapper versions captured.
Build + unit tests: PASS; exact wrapper command, exit 0, 24 tests and run-specific reports recorded.
Integration tests: BLOCKED; disposable PostgreSQL is unavailable; no production fallback.
Combined coverage gate: NOT RUN; in this example it depends on the incomplete integration suite.
Independent unit-only coverage may still be collected, but cannot satisfy this combined gate.
Dependency scan: NOT RUN; advisory source unavailable.
Verdict: readiness not established. Restore the test dependency, then rerun affected gates.
No deployment, push, merge or quality-threshold changes performed.
