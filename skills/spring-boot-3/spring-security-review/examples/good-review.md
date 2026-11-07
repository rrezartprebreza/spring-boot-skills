# Scoped review example

Illustrative finding, not an assessment of a real deployment.

- Status: confirmed in a local fixture; production impact not verified.
- Boundary: authenticated document read.
- Evidence: a synthetic USER can retrieve a different owner's document when the service
  delegates to an unscoped findById. Record the actual source path and test output in a real review.
- Fix: require documents:read and query by document ID, trusted tenant ID and trusted account ID.
- Verification: allowed read returns the expected body; wrong owner and wrong tenant return
  no document; missing scope never invokes the store.
- Remaining gap: verify the application's HTTP chain and persisted query against its database.
