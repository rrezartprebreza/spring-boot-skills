---
name: testing-pyramid
description: >
  Use when writing tests of any kind — unit, slice, or integration. Covers test structure,
  naming conventions, Mockito patterns, @WebMvcTest, @DataJpaTest, and Testcontainers setup.
---

# Testing Pyramid

## Structure

```
Unit Tests         — fast domain logic checks without a Spring context
Slice Tests        — focused web or persistence infrastructure checks
Integration Tests  — cross-boundary behavior with real dependencies
```

## Unit Tests — Services

Choose the mix by risk, not a fixed percentage. Use real database/Redis tests for concurrency,
serialization and transaction semantics that mocks cannot establish. Preserve the project's
JUnit/AssertJ and mocking conventions; changing assertion libraries is not a correctness fix.

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks private OrderService orderService;

    @Test
    void createOrder_whenItemsAvailable_shouldSaveAndReturnOrder() {
        // Given
        var request = new CreateOrderRequest("user@example.com", List.of(new OrderItemRequest(UUID.randomUUID(), 2)));
        var savedOrder = Order.create("user@example.com");
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        doNothing().when(inventoryService).reserve(any());

        // When
        Order result = orderService.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCustomerEmail()).isEqualTo("user@example.com");
        verify(inventoryService).reserve(request.items());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_whenInventoryUnavailable_shouldThrowException() {
        // Given
        var request = new CreateOrderRequest("user@example.com", List.of());
        doThrow(new InsufficientInventoryException("Out of stock"))
            .when(inventoryService).reserve(any());

        // When / Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessage("Out of stock");
    }
}
```

## Slice Tests — Controllers (@WebMvcTest)

```java
// POSTs need .with(csrf()): @WebMvcTest applies Boot's default security (CSRF on);
// @WithMockUser only authenticates. Add @Import(SecurityConfig.class) to test your real rules.
// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OrderService orderService;

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_withValidRequest_shouldReturn201() throws Exception {
        // Given
        var request = new CreateOrderRequest("user@example.com",
            List.of(new OrderItemRequest(UUID.randomUUID(), 1))); // items are @NotEmpty
        var order = Order.create("user@example.com");
        when(orderService.createOrder(any())).thenReturn(order);

        // When / Then
        mockMvc.perform(post("/api/v1/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.customerEmail").value("user@example.com"));
    }

    @Test
    @WithMockUser
    void createOrder_withInvalidRequest_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")) // missing required fields
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
```

## Slice Tests — Repositories (@DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE) // use real DB (Testcontainers)
@Import(TestcontainersConfig.class)
class OrderRepositoryTest {

    @Autowired OrderRepository orderRepository;

    @Test
    void findByStatus_shouldReturnMatchingOrders() {
        // Given
        var order1 = orderRepository.save(Order.create("a@example.com"));
        var order2 = orderRepository.save(Order.create("b@example.com"));
        order2.ship(); // change status
        orderRepository.save(order2);

        // When
        List<Order> pending = orderRepository.findByStatus(OrderStatus.PENDING, Pageable.unpaged()).getContent();

        // Then
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getCustomerEmail()).isEqualTo("a@example.com");
    }
}
```

## Integration Tests — Testcontainers

This application-level example assumes the first-party JWT setup and order API from
the related skills. Supply `src/test/resources/fixtures/order-user-and-product.sql` for
your application's schema: an enabled USER named `buyer@example.com` and an in-stock
product with ID `00000000-0000-0000-0000-000000000001`. Run it only against the disposable
test database. Adapt the fixtures and response DTO to your application.

Load the real persisted user before signing the token. `@WithMockUser` does not authenticate
a request sent over HTTP to a random-port server. For OAuth2 resource servers, use a test
issuer instead of introducing this application's JWT service.

```java
// Shared config — reuse container across tests
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() { // org.testcontainers.postgresql.PostgreSQLContainer (TC 2)
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}

// Full integration test
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
class OrderIntegrationTest {

    @Autowired RestTestClient restTestClient;
    @Autowired OrderRepository orderRepository;
    @Autowired JwtService jwtService;
    @Autowired UserDetailsService userDetailsService;

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ParameterizedTypeReference<ApiResponse<OrderResponse>> RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    @Test
    @Sql("/fixtures/order-user-and-product.sql")
    void createAndRetrieveOrder_endToEnd() {
        var user = userDetailsService.loadUserByUsername("buyer@example.com");
        String token = jwtService.generateAccessToken(user);
        var createRequest = new CreateOrderRequest("buyer@example.com",
            List.of(new CreateOrderRequest.OrderItemRequest(PRODUCT_ID, 1)));
        var created = restTestClient.post()
            .uri("/api/v1/orders")
            .headers(headers -> headers.setBearerAuth(token))
            .body(createRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(RESPONSE_TYPE)
            .returnResult().getResponseBody();
        assertThat(created).isNotNull();
        assertThat(created.success()).isTrue();
        assertThat(created.data()).isNotNull();
        UUID id = created.data().id();
        assertThat(id).isNotNull();
        assertThat(orderRepository.findById(id)).isPresent();

        var retrieved = restTestClient.get()
            .uri("/api/v1/orders/{id}", id)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk()
            .expectBody(RESPONSE_TYPE)
            .returnResult().getResponseBody();
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.success()).isTrue();
        assertThat(retrieved.data()).isNotNull();
        assertThat(retrieved.data().id()).isEqualTo(id);
        assertThat(retrieved.data().status()).isEqualTo(created.data().status());
        assertThat(retrieved.data().items()).hasSize(1);
        assertThat(retrieved.data().items().get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(retrieved.data().items().get(0).quantity()).isEqualTo(1);
    }
}
```

## Security regression matrix

For protected reads and writes, cover anonymous requests, invalid/expired credentials,
missing authority, same-tenant wrong owner, cross-tenant access, and the allowed caller.
Exercise the real SecurityFilterChain and method-security proxy where those enforce policy;
a mock Authentication or direct service call does not validate JWT decoding or proxy wiring.
Preserve the application's 401/403/404 contract instead of imposing one globally.

For denied writes, assert unchanged persisted state and no external effect, not just the
response status. For denied reads, assert that no other owner's data reaches the response.
Use the actual database query for tenant/owner isolation tests; mocks only prove delegation.
Keep CSRF enabled for cookie-authenticated browser flows and explicitly test missing/invalid
CSRF tokens. Do not disable filters or add permissive test security merely to get green tests.

Include secret-free error/log assertions, audit rollback/sink-failure cases, and bounded
AI-tool calls where those features exist. Keep adversarial fixtures synthetic and offline.
Running active scans or provider-backed evaluations requires the relevant scope and approval.

## Naming Convention

```
// Method name: methodName_condition_expectedBehavior
createOrder_whenItemsAvailable_shouldSaveOrder()
findById_whenOrderNotFound_shouldThrowNotFoundException()
login_withInvalidCredentials_shouldReturn401()
```

## Testcontainers Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId> <!-- Testcontainers 2 renamed every module -->
    <scope>test</scope>
</dependency>
```

## Verification scope

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
selected templates and an equivalent HTTP workflow; application-specific inline fixtures still need adaptation. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas
- Agent uses `org.testcontainers:postgresql` on Boot 4 — Testcontainers 2 (managed by Boot 4.1) publishes `testcontainers-postgresql`; the old artifact has no managed version. Import `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic); the old `containers` package is deprecated
- Agent uses `@SpringBootTest` for everything — use slices for speed
- Agent uses `H2` in-memory DB for `@DataJpaTest` — use Testcontainers for accuracy
- Agent uses `@MockBean` — removed in Boot 4; use `@MockitoBean` (and `@MockitoSpyBean` for spies)
- Agent uses `TestRestTemplate` by habit — prefer `RestTestClient` with `@AutoConfigureRestTestClient` for Boot 4 HTTP integration tests
- Agent relies only on mocks for transaction or concurrency guarantees - test those boundaries with real infrastructure.
- Agent forgets `@WithMockUser` on controller tests — security filter blocks all requests with 401
- Agent POSTs in `@WebMvcTest` without `.with(csrf())` — Boot's default security enables CSRF, so every write returns 403 even with `@WithMockUser`
- Agent checks only a response status - also assert persisted state, ownership and the relevant failure effects.
- Agent names tests `test_createOrder()` — use `createOrder_condition_expected()` pattern
