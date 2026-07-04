// ✅ GOOD — RepresentationModelAssembler, conditional links, PagedResourcesAssembler

@Component
@RequiredArgsConstructor
public class OrderModelAssembler
        implements RepresentationModelAssembler<Order, EntityModel<OrderResponse>> {

    @Override
    public EntityModel<OrderResponse> toModel(Order order) {
        EntityModel<OrderResponse> model = EntityModel.of(
            OrderResponse.from(order),
            linkTo(methodOn(OrderController.class).getById(order.getId())).withSelfRel(),
            linkTo(methodOn(OrderController.class).list(null, null)).withRel("orders"));

        // Conditional action links — only when the action is valid
        if (order.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(OrderController.class)
                .confirm(order.getId())).withRel("confirm"));
            model.add(linkTo(methodOn(OrderController.class)
                .cancel(order.getId())).withRel("cancel"));
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            model.add(linkTo(methodOn(OrderController.class)
                .ship(order.getId())).withRel("ship"));
        }

        return model;
    }
}

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderModelAssembler assembler;

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<OrderResponse>> getById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(assembler.toModel(order));
    }

    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<OrderResponse>>> list(
            Pageable pageable,
            PagedResourcesAssembler<Order> pagedAssembler) {
        Page<Order> orders = orderService.findAll(pageable);
        PagedModel<EntityModel<OrderResponse>> pagedModel =
            pagedAssembler.toModel(orders, assembler);
        return ResponseEntity.ok(pagedModel);
    }
}
