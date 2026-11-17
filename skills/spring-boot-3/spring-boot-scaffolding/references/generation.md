# Metadata-driven generation

1. Fetch JSON metadata from the approved Initializr endpoint (normally https://start.spring.io).
   Inspect the advertised project types, languages, Boot/Java versions, packaging and dependency
   compatibility ranges. Parse JSON with a structured parser, not grep against a version string.
   On a transient fetch failure, retry at most once with a bounded timeout, then report the
   blocker. Do not silently use stale cached metadata or another provider.
2. Select the exact approved major/release. Metadata defaults are not user approval for an
   upgrade. Stable identifiers commonly have no suffix; exclude milestone/RC/SNAPSHOT versions
   unless requested. Record the chosen identifier rather than guessing or stripping characters.
3. Encode parameters with an HTTP client's form/query encoder. Use timeouts and fail on HTTP
   errors. Do not interpolate project names into shell code. Send no credentials/private source.
4. Download to a new temporary location, not the target checkout. Confirm the response is the
   expected ZIP, not an HTML/JSON error. Inspect entries before extraction: reject absolute or
   traversal paths, symlinks, collisions and excessive expanded size. Extract into a fresh
   staging directory with a safe archive API; never overwrite existing project files.
5. Inspect scripts, wrapper metadata, dependency management and the requested capabilities.
   Check the destination and its parents for symlinks and existing content before moving the
   staged project. Stop on collisions, including a destination created during the operation.
6. Verify the effective build and tests with the actual toolchain. Do not run arbitrary scripts
   included in an unexpected response. Report the provider, selected versions, input options
   and completed checks; missing remote availability is a blocker, not permission to use a fork.

For Maven, verify the generated parent/BOM and compiler target. For Gradle, retain the selected
DSL, wrapper and Boot plugin; confirm Java toolchain and plugin compatibility. Do not manually
upgrade either tool as a side effect of scaffolding.
