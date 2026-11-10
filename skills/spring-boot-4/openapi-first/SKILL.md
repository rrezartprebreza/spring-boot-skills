---
name: openapi-first
description: >
  Use when the project follows API-first / OpenAPI-first approach: generating controller
  interfaces, DTOs, and clients from an OpenAPI spec. Use when you see openapi.yaml,
  openapi-generator-maven-plugin, or ApiDelegate pattern in the project.
---

# OpenAPI-First Development

## Maven Plugin Setup

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.26.0</version> <!-- useSpringBoot4/useJackson3 need 7.20+ -->
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <apiPackage>com.example.api</apiPackage>
                <modelPackage>com.example.api.model</modelPackage>
                <configOptions>
                    <delegatePattern>true</delegatePattern>      <!-- implement delegate, not controller -->
                    <interfaceOnly>false</interfaceOnly>
                    <useSpringBoot4>true</useSpringBoot4>
                    <useJackson3>true</useJackson3>
                    <useTags>true</useTags>
                    <dateLibrary>java8</dateLibrary>
                    <openApiNullable>false</openApiNullable>
                    <skipDefaultInterface>false</skipDefaultInterface> <!-- must stay false with delegatePattern: the controller instantiates a no-op delegate -->
                </configOptions>
                <!-- NOT generateSupportingFiles=true: that adds a second @SpringBootApplication main class
                     (repackage fails: "Unable to find a single main class") and a SpringDocConfiguration -->
                <supportingFilesToGenerate>ApiUtil.java</supportingFilesToGenerate>
                <modelNameSuffix>Dto</modelNameSuffix> <!-- OrderDto, not Order: no clash with domain types -->
                <output>${project.build.directory}/generated-sources/openapi</output>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## OpenAPI Spec Example

```yaml
# src/main/resources/openapi.yaml
openapi: 3.0.3
info:
  title: Order Service API
  version: 1.0.0

paths:
  /api/v1/orders:
    post:
      tags: [Orders]
      operationId: createOrder
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateOrderRequest'
      responses:
        '201':
          description: Order created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderResponse'
        '400':
          $ref: '#/components/responses/ValidationError'

    get:
      tags: [Orders]
      operationId: listOrders
      parameters:
        - name: page
          in: query
          schema: { type: integer, default: 0 }
        - name: size
          in: query
          schema: { type: integer, default: 20 }
      responses:
        '200':
          description: Paginated orders
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderPage'

components:
  schemas:
    CreateOrderRequest:
      type: object
      required: [customerEmail, items]
      properties:
        customerEmail:
          type: string
          format: email
        items:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/OrderItemRequest'

    OrderResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        status:
          type: string
          enum: [PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED]
        customerEmail:
          type: string
        createdAt:
          type: string
          format: date-time

    OrderItemRequest:
      type: object
      required: [productId, quantity]
      properties:
        productId:
          type: string
          format: uuid
        quantity:
          type: integer
          minimum: 1

    OrderPage:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/OrderResponse'
        page:
          type: integer
        size:
          type: integer
        totalElements:
          type: integer
          format: int64

    ApiError:
      type: object
      properties:
        code:
          type: string
        message:
          type: string
        details:
          type: array
          items:
            type: string

  responses:
    ValidationError:
      description: Validation failed
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ApiError'
```

## Implementing the Delegate

```java
// Generated: OrdersApi interface with delegate
// Your implementation — never modify generated files

@Service
@RequiredArgsConstructor
public class OrdersApiDelegateImpl implements OrdersApiDelegate {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderResponse> createOrder(CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(OrderApiMapper.toResponse(order));
    }

    @Override
    public ResponseEntity<OrderPage> listOrders(Integer page, Integer size) {
        Page<Order> orders = orderService.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(OrderApiMapper.toPage(orders));
    }
}
```

## .gitignore — Never Commit Generated Files

```gitignore
target/generated-sources/openapi/
```

## Gotchas
- Agent modifies generated controller files — NEVER modify generated code, implement delegate
- Agent configures a Boot 4 build with `useSpringBoot3=true` on an old generator — use generator 7.20+ with `useSpringBoot4=true` and `useJackson3=true`
- Agent commits generated sources — add to `.gitignore`, generate on build
- Agent sets `skipDefaultInterface=true` with `delegatePattern=true` — the generated controller instantiates a no-op delegate, so it stops compiling; keep it `false` and cover every operation with a contract test
- Agent sets `generateSupportingFiles=true` — generates a second `@SpringBootApplication` and SpringDoc config; list only `ApiUtil.java` in `supportingFilesToGenerate`
- Agent mixes generated models with hand-written models — keep them separate
