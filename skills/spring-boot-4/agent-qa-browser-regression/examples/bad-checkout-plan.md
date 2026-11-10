# Bad: treating preparation as a release pass

Illustrative anti-pattern; do not use this as a release gate.

```sh
# Incorrect: no browser behavior is executed or asserted here.
agent-qa validate tests/checkout.yaml
agent-qa run tests/checkout.yaml --dry-run
printf '%s\n' 'Checkout passed on the new release'
```

The success message is unsupported. Neither preparation command proves the UI origin is correct or that the new application build passed. An Actuator health response and an older memidex success do not fill that gap. Disabling CSRF to bypass a failed login or deleting contradictory memory would conceal the problem. Keep the original assertions and inspect current execution evidence instead.
