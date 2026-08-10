@RestController
final class OrderController {
    @GetMapping(value = "/orders/{id}", version = "1.0")
    Order get(UUID id) { return service.get(id); } // Framework 7 API used on Boot 3
}
