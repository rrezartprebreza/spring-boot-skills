# Good: scoped checkout verification

Illustrative plan for an existing agent-qa project; these commands were not run against a live application as part of this catalog contribution.

The deployed UI and its Spring Boot backend use the project's existing test environment, security rules, and disposable checkout fixture. Run the existing backend checks. Confirm the UI origin and deployed build before using the selected test; do not substitute the management health endpoint.

```sh
# From the agent-qa project; the test and CLI already exist.
: "${APP_BUILD_TAG:?Set the actual deployed application build}"
agent-qa validate tests/checkout.yaml
agent-qa run tests/checkout.yaml --dry-run
# Proceed only with authorization for this journey's application mutations.
agent-qa run tests/checkout.yaml --build-tag "$APP_BUILD_TAG"
```

Capture the returned run ID. Read its digest and overview; use the actual indices before opening step evidence. A failed assertion remains a failure even if an older memory record describes success. Report missing evidence and use scoped fixture cleanup after the run. Login steps use secret references from `workspace.secretsFile`; keep the real authentication/CSRF flow.

```sh
# Replace RUN_ID with the recorded ID, never a guessed value.
agent-qa digest RUN_ID --json
agent-qa runs overview RUN_ID --json
agent-qa runs knowledge RUN_ID --json
```
