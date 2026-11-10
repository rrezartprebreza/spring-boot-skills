// GOOD — controller slice: only the web layer, collaborators mocked with @MockitoBean.
// Writes need .with(csrf()): @WebMvcTest applies Boot's default security (CSRF on), and
// @WithMockUser only authenticates — without csrf() every POST/PUT/DELETE returns 403.
@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
}
