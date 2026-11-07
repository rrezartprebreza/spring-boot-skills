# Security skill verification

This fixture compiles the actual Java templates shipped in both skill trees. It uses the
repository's Boot 3.5.16 / Boot 4.1.0 BOM baselines, with CI on Java 17 and 21. It needs no
database, model provider, scanner account, production target or credentials.

From the repository root:

```sh
mvn --batch-mode --no-transfer-progress -f verification/security/pom.xml -Pboot3 clean test
mvn --batch-mode --no-transfer-progress -f verification/security/pom.xml -Pboot4 clean test
python3 -m unittest discover -s scripts -p 'test_security_helpers.py'
```

Use `clean` when switching the version profile locally so old compiled classes cannot mask
a missing source. The Python helpers use only the standard library (Python 3.10+).

## What is checked

- `DocumentAccess`: unauthenticated/anonymous/wrong-principal and scope rejection before
  lookup, propagation of trusted identity, and owner/tenant separation in an in-memory store.
- `DocumentReadTool`: immutable caller permissions, canonical UUID input, server-side scope,
  concurrent budgets shared across replacement tools, denied/invalid/failed attempts consuming
  budget, and bounded returned output (not fetched bytes). Hostile document
  text remains untrusted content; the fixture does not claim to detect prompt injection.
- `SecurityAuditRecord`: required event metadata, typed outcomes, verified identities for
  success, and rejection of oversized/control-character correlation IDs.
- `sbom_inventory.py`: both shipped copies parse nested synthetic components, flag missing
  metadata, reject malformed/duplicate-key/deep/oversized input, and expose no assessment verdict.
- `evidence_manifest.py`: both copies hash local snapshots without copying content/paths to
  the output, reject changed/non-regular/symlink inputs, and produce JSON from the CLI.

## Limits

The Java storage implementations are test doubles, not database isolation proof. The fixture
does not test Spring HTTP/proxy wiring, Spring AI tool registration, vector-store authorization,
live models, a durable audit sink or outbox, vulnerability feeds, image signing, or production
incident response. Add those tests in the consuming application's actual environment.

The corresponding agent cases in `evaluations/cases.json` define response-review criteria.
Validating their JSON does not demonstrate Claude Code or Codex behavior. No unattended model
calls, external uploads or active security scans are added to CI.
