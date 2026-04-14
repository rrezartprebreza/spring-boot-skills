---
name: oauth2-resource-server
description: >
  Use when configuring Spring Boot as an OAuth2 resource server, validating JWTs from
  an external auth provider (Keycloak, Auth0, Okta, Cognito), extracting claims, or
  implementing scope-based authorization.
---

# OAuth2 Resource Server

## Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
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
        var converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles"); // Keycloak uses "roles"
        converter.setAuthorityPrefix("ROLE_");

        var authConverter = new JwtAuthenticationConverter();
        authConverter.setJwtGrantedAuthoritiesConverter(converter);
        return authConverter;
    }
}
```

## application.yml — Common Providers

```yaml
# Keycloak
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/my-realm
          jwk-set-uri: https://keycloak.example.com/realms/my-realm/protocol/openid-connect/certs

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

    public UUID getUserId(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getClaimAsString("sub"));
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

```java
@RestController
@RequiredArgsConstructor
public class OrderController {

    @GetMapping("/api/v1/orders/my")
    public ApiResponse<List<OrderResponse>> myOrders(
        @AuthenticationPrincipal Jwt jwt  // inject JWT directly
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.ok(orderService.findByUser(userId));
    }

    // Or with JwtAuthenticationToken for full principal
    @GetMapping("/api/v1/profile")
    public ApiResponse<ProfileResponse> profile(JwtAuthenticationToken token) {
        return ApiResponse.ok(userService.findByEmail(
            token.getToken().getClaimAsString("email")
        ));
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
        UUID userId = UUID.fromString(jwt.getSubject());
        return orderRepository.existsByIdAndCustomerId(orderId, userId);
    }
}
```

## Gotchas
- Agent uses `hasRole("ADMIN")` for scope check — scopes use `hasAuthority("SCOPE_admin")`
- Agent forgets `issuer-uri` validation — always configure to prevent token forgery
- Agent maps roles wrong for Keycloak — roles are nested under `realm_access.roles`
- Agent uses `getPrincipal()` directly — cast to `Jwt` or use `@AuthenticationPrincipal Jwt`
- Agent adds `userDetailsService` bean — not needed for resource servers (stateless JWT)
