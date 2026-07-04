// ✅ GOOD — UUID ID, STRING enum, LAZY fetch, behavior methods, no public setters

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_customer_email", columnList = "customer_email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;

    public static Order create(String customerEmail, User user) {
        Order order = new Order();
        order.customerEmail = customerEmail;
        order.user = user;
        order.status = OrderStatus.PENDING;
        return order;
    }

    public void addItem(Product product, int quantity) {
        if (status != OrderStatus.PENDING)
            throw new IllegalStateException("Cannot add items to order in status: " + status);
        items.add(OrderItem.create(this, product, quantity));
    }

    public void confirm() {
        if (status != OrderStatus.PENDING)
            throw new IllegalStateException("Can only confirm PENDING orders");
        this.status = OrderStatus.CONFIRMED;
    }

    public void ship() {
        if (status != OrderStatus.CONFIRMED)
            throw new IllegalStateException("Can only ship CONFIRMED orders");
        this.status = OrderStatus.SHIPPED;
    }
}
