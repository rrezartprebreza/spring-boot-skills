// ❌ BAD — anemic model, no invariant enforcement, no typed IDs, no events

@Entity
@Data                                        // generates public setters — anyone can mutate state
@NoArgsConstructor                           // public no-arg constructor allows invalid objects
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                         // Long ID — not a typed value object

    private Long customerId;                 // Long — should be CustomerId value object

    private String customerEmail;            // raw String — should be EmailAddress value object

    @Enumerated(EnumType.ORDINAL)            // ORDINAL breaks if enum is reordered
    private OrderStatus status;

    private BigDecimal total;                // raw BigDecimal — should be Money value object

    @OneToMany(mappedBy = "order")           // missing cascade, orphanRemoval
    private List<OrderItem> items;           // not initialized — NPE risk

    // No behavior methods — all logic will live in service:
    // - No addItem() method
    // - No place() method
    // - No state transition validation
    // - No domain events
    // - Total is set manually from outside, not calculated

    // External code does:
    // order.getItems().add(new OrderItem(...));  // bypasses aggregate boundary
    // order.setStatus(OrderStatus.PLACED);       // no invariant check
    // order.setTotal(calculateTotal(items));      // logic outside domain
}
