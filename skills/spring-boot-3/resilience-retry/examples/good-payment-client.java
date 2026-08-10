@Retryable(
    retryFor = ConnectException.class,
    maxAttempts = 4,
    backoff = @Backoff(delay = 200, multiplier = 2.0))
PaymentResult charge(ChargeRequest request) { return remote.charge(request); }
