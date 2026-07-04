// ❌ BAD — every common JPA mistake

@Entity
@Data                                   // @Data generates setters — bad for entities
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // exposes auto-increment Long
    private Long id;                    // Long ID, not UUID

    private String customerEmail;       // missing @Column constraints

    @Enumerated(EnumType.ORDINAL)       // ORDINAL breaks if enum is reordered
    private OrderStatus status;

    @OneToMany                          // missing cascade, orphanRemoval
    private List<OrderItem> items;      // not initialized — NPE waiting to happen

    @ManyToOne                          // missing fetch = LAZY — EAGER by default = N+1
    private User user;

    // No @CreationTimestamp / @UpdateTimestamp
    // No @Version for optimistic locking
    // No factory method — public constructor allows invalid state
    // No behavior methods — service has to know internal rules
}
