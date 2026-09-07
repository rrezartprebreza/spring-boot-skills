---
name: spring-security-jwt
description: >
  Use when an application issues and validates its own first-party JWT access and refresh tokens,
  including authentication filters, password encoding, RBAC, and method security. For JWTs issued
  by Keycloak, Auth0, Okta, Cognito, or another authorization server, use oauth2-resource-server.
---

# Spring Security — JWT

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

## Security configuration

Use the compiled [SecurityConfig](templates/SecurityConfig.java) with the filter and service below.
It uses the lambda DSL, stateless bearer authentication, role rules, JSON 401/403 handlers and
disabled servlet registration for the security-chain filter. Bean method injection avoids
constructor cycles between the configuration and its own AuthenticationProvider bean.
Adapt the routes and roles to the project. CSRF disabling applies to header-only bearer APIs;
keep CSRF protection when browsers send authentication cookies automatically.

## JWT implementation

Use the tested [JwtService](templates/JwtService.java) and
[JwtAuthenticationFilter](templates/JwtAuthenticationFilter.java) templates together.
The configuration keys are `app.jwt.secret`, `app.jwt.access-token-expiration`, and
`app.jwt.refresh-token-expiration` (durations in milliseconds).

The filter rejects expired, malformed, tampered, missing-expiration and refresh tokens with
401 and a Bearer challenge. It checks the current user's enabled, locked, account-expired and
credentials-expired flags before authentication. Deleted users also receive 401.
Database outages and downstream application failures must remain server failures, not be
masked as invalid credentials. Invalid supplied tokens are rejected even on public endpoints.

The filter's example error body uses Problem Details. For an existing legacy API, adapt this
response and the entry point below to the established error contract. Never log bearer tokens.
Register a filter bean only in the security chain: disable servlet-container registration
with a `FilterRegistrationBean<JwtAuthenticationFilter>` whose `enabled` flag is false.

These templates illustrate a single-service first-party token contract. Before sharing signing
keys or accepting tokens across services, define and validate issuer and audience, key rotation,
and revocation. Spring Security's resource-server support can also validate custom JWTs; preserve
it when it already fits the application. Token generation is not a complete refresh flow:
retain the rotation/reuse-detection requirements below.

## JSON 401/403

The configuration and filter templates use Problem Details for authentication and authorization
errors. Adapt both together for a legacy error contract. Missing credentials on a protected route
return 401; an authenticated caller without the required role returns 403. An invalid supplied
token returns 401 even when the route permits anonymous access.
Controller advice cannot handle exceptions thrown before the dispatcher servlet.

## Auth Controller

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(authService.register(request)));
    }
}

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {}
```

## Method-Level Security

```java
// On service methods
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(UUID userId) { ... }

@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
public UserProfile getProfile(UUID userId) { ... }

@PostAuthorize("returnObject.email == authentication.name")
public User findById(UUID id) { ... }
```

## application.yml

```yaml
app:
  jwt:
    secret: ${JWT_SECRET} # min 256-bit base64 encoded key
    access-token-expiration: 900000   # 15 minutes
    refresh-token-expiration: 604800000 # 7 days
```

The example creates refresh tokens but does not implement a refresh endpoint. A production refresh
flow must accept only `type=refresh`, rotate the refresh token on every use, and revoke the previous
token (for example, with a hashed token-family record in a database or Redis).

## Gotchas
- Agent catches AuthenticationException broadly around user lookup - preserve AuthenticationServiceException as a server failure.
- Agent logs in disabled or locked users from valid JWTs - validate current account status as well as claims.
- Agent uses `HttpSecurity.csrf().disable()` old API — use `AbstractHttpConfigurer::disable`
- Agent lets `ExpiredJwtException` escape the filter — expired token becomes a 500 instead of 401; catch in filter
- Agent skips `exceptionHandling()` — clients get empty 401/403 bodies (or a login-page redirect); `@RestControllerAdvice` can't catch filter-level exceptions
- Agent stores JWT secret in code — always `${JWT_SECRET}` from environment (HS256 needs a ≥256-bit key or `Keys.hmacShaKeyFor` throws `WeakKeyException`)
- Agent uses `SessionCreationPolicy.IF_REQUIRED` — must be `STATELESS` for JWT
- Agent validates only signature and expiry — the bearer filter must accept `type=access` tokens only; refresh tokens belong to a separate refresh endpoint
- Agent forgets `@EnableMethodSecurity` for `@PreAuthorize` to work
- Agent uses BCrypt strength < 10 — use 12 for production
- Agent puts token validation logic in controller — belongs in filter
- Agent puts refresh tokens in localStorage examples — recommend httpOnly cookies or secure storage; refresh tokens are long-lived credentials
