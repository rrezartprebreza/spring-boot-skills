---
name: oauth2-resource-server
description: >
  Use when configuring Spring Boot as an OAuth2 resource server, validating JWTs from
  an external auth provider (Keycloak, Auth0, Okta, Cognito), extracting claims, or
  implementing scope-based authorization.
---

# OAuth2 Resource Server

Spring Boot 4.x ships **Spring Security 7** — lambda DSL only; `and()`, `authorizeRequests()`,
`antMatchers()`, and `AntPathRequestMatcher`/`MvcRequestMatcher` are gone
(`requestMatchers("/path/**")` is backed by `PathPatternRequestMatcher`).

## Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

## Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        // Keep the defaults: the "scope" OR "scp" claim (Okta/Entra use scp) -> SCOPE_*.
        // Calling setAuthoritiesClaimName(...) replaces them — then hasAuthority("SCOPE_admin")
        // can never pass. Extend the defaults with Keycloak realm roles instead.
        var scopes = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            // Keycloak nests realm roles under realm_access.roles -> ROLE_* (for hasRole)
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}
```

## application.yml — Common Providers

Choose the provider configuration matching your issuer; these are alternatives, not duplicate
`spring:` mappings to paste into one YAML document.

```yaml
# Keycloak
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/my-realm
          jwk-set-uri: https://keycloak.example.com/realms/my-realm/protocol/openid-connect/certs
          audiences: my-api   # without it, tokens minted for ANY client in the realm are accepted
```

```yaml
# Auth0
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-domain.auth0.com/
          audiences: https://your-api.example.com  # custom claim validation
```

## Custom Claim Extraction

```java
@Component
public class JwtClaimExtractor {

    // Provider subjects are opaque strings ("auth0|64f1c2", Okta "00u1abcd..."), not UUIDs.
    public String getSubject(JwtAuthenticationToken token) {
        return token.getToken().getSubject();
    }

    public String getEmail(JwtAuthenticationToken token) {
        return token.getToken().getClaimAsString("email");
    }

    public List<String> getRoles(JwtAuthenticationToken token) {
        // Keycloak nests roles under realm_access.roles
        Map<String, Object> realmAccess = token.getToken().getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        return (List<String>) realmAccess.getOrDefault("roles", List.of());
    }
}
```

## Controller — Accessing Current User

Use the exact `(iss, sub)` pair as the external identity key, with a database unique
constraint on that pair. Email and username are optional, mutable profile attributes,
not account lookup or automatic account-linking keys, even when email is verified.
The [ExternalIdentity template](templates/ExternalIdentity.java) accepts an already
verified access token; signature, issuer, audience and expiry validation must happen first.
Allow only configured issuers; never discover an arbitrary issuer URL from an untrusted token.
For existing subject-only mappings, backfill the known issuer before introducing a second issuer.
Account linking requires an explicit authenticated flow proving control of both accounts.

See [OpenID claim stability](https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability).

```java
@RestController
@RequiredArgsConstructor
public class OrderController {

    @GetMapping("/api/v1/orders/my")
    public ApiResponse<List<OrderResponse>> myOrders(
        @AuthenticationPrincipal Jwt jwt  // inject JWT directly
    ) {
        ExternalIdentity identity = ExternalIdentity.from(jwt);
        return ApiResponse.ok(orderService.findByOwner(identity.issuer(), identity.subject()));
    }

    // Or with JwtAuthenticationToken for full principal
    @GetMapping("/api/v1/profile")
    public ApiResponse<ProfileResponse> profile(JwtAuthenticationToken token) {
        ExternalIdentity identity = ExternalIdentity.from(token.getToken());
        return ApiResponse.ok(userService.findByExternalIdentity(identity.issuer(), identity.subject()));
    }
}
```

## Method Security with Scopes

```java
@PreAuthorize("hasAuthority('SCOPE_orders:read')")
public List<Order> findAll() { ... }

@PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#orderId, authentication)")
public Order findById(UUID orderId) { ... }

// Custom security bean
@Component("orderSecurity")
public class OrderSecurityService {
    public boolean isOwner(UUID orderId, Authentication auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        ExternalIdentity identity = ExternalIdentity.from(jwt);
        return orderRepository.existsByIdAndOwnerIssuerAndOwnerSubject(
            orderId, identity.issuer(), identity.subject());
    }
}
```

## Verification scope

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
signed-token validation and issuer/subject mapping; it does not contact a production identity provider. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas
- Agent finds or merges accounts by email - use the verified issuer/subject pair; email changes must not change ownership.
- Agent calls `setAuthoritiesClaimName(...)` on `JwtGrantedAuthoritiesConverter` — it replaces the default `scope`/`scp` lookup, so `SCOPE_*` authorities vanish (and Okta/Entra `scp` scopes are dropped); extend the defaults instead
- Agent parses `jwt.getSubject()` with `UUID.fromString` — Auth0 (`auth0|...`) and Okta (`00u...`) subjects aren't UUIDs; the request fails with 500
- Agent omits `audiences` — the resource server then accepts any token the issuer minted for any client
- Agent uses `hasRole("ADMIN")` for scope check — scopes use `hasAuthority("SCOPE_admin")`
- Agent forgets `issuer-uri` validation — always configure to prevent token forgery
- Agent maps roles wrong for Keycloak — roles are nested under `realm_access.roles`
- Agent uses `getPrincipal()` directly — cast to `Jwt` or use `@AuthenticationPrincipal Jwt`
- Agent adds `userDetailsService` bean — not needed for resource servers (stateless JWT)
- Agent adds the Boot 3 starter `spring-boot-starter-oauth2-resource-server` — Boot 4 renamed security starters; use `spring-boot-starter-security-oauth2-resource-server`
- Agent uses non-lambda chaining (`.oauth2ResourceServer().jwt()`, `.and()`) — removed in Security 7, won't compile; lambda DSL only
- Agent writes `antMatchers()` / `AntPathRequestMatcher` — removed in Security 7; use `requestMatchers("/path/**")` (backed by `PathPatternRequestMatcher`)
- Agent mocks `JwtDecoder` with `@MockBean` in tests — removed in Boot 4; use `@MockitoBean` (and `@AutoConfigureMockMvc` — `@SpringBootTest` no longer provides `MockMvc`)
