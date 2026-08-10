@SpringBootTest
class OrderControllerTest {
    @MockBean OrderService service; // removed override and unnecessary full context
}
