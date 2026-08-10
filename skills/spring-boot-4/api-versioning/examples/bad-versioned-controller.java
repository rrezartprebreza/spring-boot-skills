@RestController
final class OrderController {
    @GetMapping("/api/v1/orders/{id}")
    Order getV1(UUID id) { return service.get(id); } // bypasses Framework 7 version mapping
}
