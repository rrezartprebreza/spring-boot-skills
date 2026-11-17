package fixtures.valid.orders;

import fixtures.valid.inventory.Inventory;

public class Orders {
    private final Inventory inventory;
    public Orders(Inventory inventory) { this.inventory = inventory; }
    public boolean canPlace() { return inventory.available() > 0; }
}
