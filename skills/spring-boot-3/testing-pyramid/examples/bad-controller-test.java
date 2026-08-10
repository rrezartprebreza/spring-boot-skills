@SpringBootTest
class OrderControllerTest {
    @MockBean OrderService service; // full context and deprecated override for a slice concern
}
