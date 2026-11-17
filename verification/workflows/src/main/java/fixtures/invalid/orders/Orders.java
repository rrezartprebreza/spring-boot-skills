package fixtures.invalid.orders;

import fixtures.invalid.inventory.internal.InternalInventory;

// Deliberately violates the inventory module's internal package boundary.
public class Orders {
    private final InternalInventory inventory;
    public Orders(InternalInventory inventory) { this.inventory = inventory; }
    public boolean canPlace() { return inventory.available() > 0; }
}
