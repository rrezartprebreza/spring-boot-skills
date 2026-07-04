---
name: hateoas
description: >
  Use when adding hypermedia links to REST responses, building self-describing APIs,
  or implementing Spring HATEOAS. Use when you see EntityModel, CollectionModel, or
  RepresentationModel in the project.
---

# Spring HATEOAS

## Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-hateoas</artifactId>
</dependency>
```

## When to Add Links

- `self` — always, on every resource response
- `collection` — link back to the list endpoint
- `related resources` — when a client commonly needs to navigate to them
- `actions` — links to state transitions (e.g., `cancel`, `ship`) when valid for current state

## Resource Model

```java
public class OrderModel extends RepresentationModel<OrderModel> {
    private final UUID id;
    private final String status;
    private final String customerEmail;
    private final Instant createdAt;

    // Static factory with links
    public static OrderModel from(Order order) {
        OrderModel model = new OrderModel(
            order.getId(), order.getStatus().name(),
            order.getCustomerEmail(), order.getCreatedAt()
        );

        // Self link — always
        model.add(linkTo(methodOn(OrderController.class).getById(order.getId())).withSelfRel());

        // Collection link
        model.add(linkTo(methodOn(OrderController.class).list(null)).withRel("orders"));

        // Conditional action links based on state
        if (order.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(OrderController.class)
                .cancelOrder(order.getId())).withRel("cancel"));
        }
        if (order.getStatus() == OrderStatus.PROCESSING) {
            model.add(linkTo(methodOn(OrderController.class)
                .shipOrder(order.getId())).withRel("ship"));
        }

        return model;
    }
}
```

## Controller

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{id}")
    public ResponseEntity<OrderModel> getById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(OrderModel.from(order));
    }

    @GetMapping
    public ResponseEntity<CollectionModel<OrderModel>> list(Pageable pageable) {
        Page<Order> orders = orderService.findAll(pageable);

        List<OrderModel> models = orders.getContent().stream()
            .map(OrderModel::from)
            .toList();

        CollectionModel<OrderModel> collection = CollectionModel.of(models,
            linkTo(methodOn(OrderController.class).list(pageable)).withSelfRel()
        );

        // Pagination links
        if (orders.hasNext()) {
            collection.add(linkTo(methodOn(OrderController.class)
                .list(pageable.next())).withRel(IanaLinkRelations.NEXT));
        }
        if (orders.hasPrevious()) {
            collection.add(linkTo(methodOn(OrderController.class)
                .list(pageable.previousOrFirst())).withRel(IanaLinkRelations.PREV));
        }

        return ResponseEntity.ok(collection);
    }
}
```

## Response Shape

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "customerEmail": "user@example.com",
  "_links": {
    "self": { "href": "http://api.example.com/api/v1/orders/550e8400" },
    "orders": { "href": "http://api.example.com/api/v1/orders" },
    "cancel": { "href": "http://api.example.com/api/v1/orders/550e8400/cancel" }
  }
}
```

## RepresentationModelAssembler Pattern
- Spring's recommended way to build HATEOAS models from entities
- Implements `RepresentationModelAssembler<Entity, Model>` — reusable across controllers
- Inject the assembler into controllers instead of calling `Model.from()` directly

```java
@Component
public class OrderModelAssembler implements RepresentationModelAssembler<Order, EntityModel<OrderResponse>> {

    @Override
    public EntityModel<OrderResponse> toModel(Order order) {
        EntityModel<OrderResponse> model = EntityModel.of(OrderResponse.from(order),
            linkTo(methodOn(OrderController.class).getById(order.getId())).withSelfRel(),
            linkTo(methodOn(OrderController.class).list(null)).withRel("orders"));

        if (order.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(OrderController.class)
                .cancelOrder(order.getId())).withRel("cancel"));
        }
        return model;
    }
}
```

## PagedModel for Paginated Collections
- Use `PagedResourcesAssembler` for automatic pagination links (first, prev, next, last)
- Inject `PagedResourcesAssembler<Order>` into controllers — Spring creates it automatically

```java
@GetMapping
public ResponseEntity<PagedModel<EntityModel<OrderResponse>>> list(
        Pageable pageable, PagedResourcesAssembler<Order> pagedAssembler) {
    Page<Order> orders = orderService.findAll(pageable);
    PagedModel<EntityModel<OrderResponse>> pagedModel =
        pagedAssembler.toModel(orders, orderModelAssembler);
    return ResponseEntity.ok(pagedModel);
}
```

## Gotchas
- Agent adds all links regardless of state — only add action links when the action is valid
- Agent hardcodes URLs in links — always use `linkTo(methodOn(...))` for type-safe links
- Agent returns plain DTO — wrap in `EntityModel.of(dto, links...)` or extend `RepresentationModel`
- Agent puts link logic in controller — extract to `RepresentationModelAssembler`
- Agent manually builds pagination links — use `PagedResourcesAssembler` instead
- Agent forgets `self` link — every resource must have a `self` link
