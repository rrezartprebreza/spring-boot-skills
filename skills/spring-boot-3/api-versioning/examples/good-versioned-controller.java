@RestController
@RequestMapping("/api/v1/orders")
final class OrderV1Controller {
    @GetMapping("/{id}")
    OrderV1 get(@PathVariable UUID id) { return service.getV1(id); }
}
