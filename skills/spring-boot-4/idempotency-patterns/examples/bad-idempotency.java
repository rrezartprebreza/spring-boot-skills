// BAD: two threads can both observe false and create two orders.
void createOrder(String key, OrderRequest request) {
    if (!results.exists(key)) {
        orders.create(request);
        results.save(key);
    }
}
// A process crash between create and save also causes duplicate execution on retry.
