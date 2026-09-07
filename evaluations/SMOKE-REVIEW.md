# Initial smoke review

One Codex response-level case, first-party-jwt, was collected and reviewed during this
change on 2026-09-07. It ran against an uncommitted worktree using the CLI's configured
default model. The full prompt, CLI version and transcript are retained in the local
ignored evaluations/results directory. This is a limited smoke check, not a comparative
benchmark or a complete run of all seven scenarios.

| Criterion | Result | Response evidence |
| --- | --- | --- |
| Select first-party Boot 4 JWT guidance | Pass | "skills/spring-boot-4/spring-security-jwt/SKILL.md" |
| Invalid/refresh tokens produce 401 and a challenge | Pass | "The filter must reject invalid access credentials with 401"; the code sets WWW-Authenticate |
| Check current account status | Pass | "accountChecker.check(user)" |
| Preserve downstream failures | Pass | "do not wrap filterChain.doFilter in the authentication catch block" |
| Preserve the authentication system | Pass | "Keep the existing UserDetailsService and access/refresh contract" |

The response also identified that catching AuthenticationException broadly can mask
AuthenticationServiceException infrastructure failures. The templates were tightened and
an executable regression test was added after this evaluation.

Claude Code could not be evaluated because its CLI is not installed in this environment.
Its runner command and the remaining scenarios are available for an authenticated run.
