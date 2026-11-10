// BAD: illustrative fragments, not a standalone compilation unit.
public void submit(UUID id) {
    this.render(id); // bypasses the Spring async proxy
}

@Async
public void render(UUID id) {
    CompletableFuture.runAsync(() -> blockingRender(id)); // hidden common-pool hop; lost failure
}
