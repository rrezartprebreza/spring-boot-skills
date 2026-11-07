# Supply-chain review example

Synthetic scenario: an advisory scanner identifies a transitive library in an application SBOM.

- Confirm the package, resolved version and affected range using the upstream advisory.
- Keep runtime dependency evidence separate from build/test dependencies.
- Record the exact artifact digest and scanner database age.
- Status: component presence confirmed; runtime exploitability not verified.
- Propose a compatible Boot-managed update, compare dependency graphs and retest the rebuilt artifact.
- Keep any temporary exception scoped, owned and time-limited.

The accompanying good-bom.json is synthetic inventory input, not a clean security assessment.
