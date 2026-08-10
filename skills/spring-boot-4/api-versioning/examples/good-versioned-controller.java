@RestController
@RequestMapping("/api/orders")
final class OrderController {
    @GetMapping(path = "/{id}", version = "1.0")
    OrderV1 getV1(@PathVariable UUID id) { return service.getV1(id); }
}
