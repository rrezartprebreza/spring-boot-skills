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
    <version>7.5.0</version>
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
                    <useSpringBoot3>true</useSpringBoot3>
                    <useTags>true</useTags>
                    <dateLibrary>java8</dateLibrary>
                    <serializationLibrary>jackson</serializationLibrary>
                    <openApiNullable>false</openApiNullable>
                    <skipDefaultInterface>true</skipDefaultInterface>
                </configOptions>
                <generateSupportingFiles>true</generateSupportingFiles>
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
- Agent generates code without `useSpringBoot3=true` — uses old `javax.*` imports
- Agent commits generated sources — add to `.gitignore`, generate on build
- Agent skips `skipDefaultInterface=true` — generates default methods that hide missing impls
- Agent mixes generated models with hand-written models — keep them separate
