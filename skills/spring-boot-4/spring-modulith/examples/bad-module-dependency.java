// BAD: checkout bypasses the inventory module's public API.
package com.example.checkout;

import com.example.inventory.internal.InventoryRepository;

class CheckoutService {
    private final InventoryRepository inventory;
    CheckoutService(InventoryRepository inventory) {
        this.inventory = inventory;
    }
}
