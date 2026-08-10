@Retryable(maxAttempts = 50)
PaymentResult charge(ChargeRequest request) {
    return this.charge(request); // self-invocation and non-idempotent retry
}
