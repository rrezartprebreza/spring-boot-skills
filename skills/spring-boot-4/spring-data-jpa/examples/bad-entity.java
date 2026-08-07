// BAD - common Boot 4 JPA mistakes.

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
@Data
public final class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long version;

    private String customerEmail;

    @Enumerated(EnumType.ORDINAL)
    private OrderStatus status;

    @OneToMany
    private List<OrderItem> items;

    @ManyToOne
    private User user;

    public Order() {
    }
}
