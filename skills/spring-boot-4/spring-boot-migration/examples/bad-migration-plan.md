# Risky migration

1. Upgrade Boot, Java, Spring Cloud, and domain behavior in one commit.
2. Add classic starters until compilation succeeds.
3. Keep both Jackson generations on the classpath.
4. Ship without running integration tests.
