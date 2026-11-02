package com.example.clients;

import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import com.example.clients.CatalogRetryService.CatalogTransport;
import com.example.clients.CatalogRetryService.TransientCatalogException;

/** Error classification belongs in the transport adapter, not in controllers. */
public final class CatalogHttpClient implements CatalogTransport {
    private final RestClient client;
    public CatalogHttpClient(RestClient client) { this.client = client; }

    public static CatalogHttpClient create(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
        if (connectTimeout == null || readTimeout == null || connectTimeout.isNegative()
                || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Positive transport timeouts required");
        }
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return new CatalogHttpClient(RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(factory).build());
    }

    @Override public String fetch(UUID id) {
        try {
            return client.get().uri("/catalog/{id}", id).retrieve().body(String.class);
        } catch (HttpServerErrorException failure) {
            int status = failure.getStatusCode().value();
            if (status == 502 || status == 503 || status == 504) {
                throw new TransientCatalogException(failure);
            }
            throw failure;
        } catch (ResourceAccessException failure) {
            boolean transientFailure = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Interrupted");
                }
                transientFailure |= cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
            }
            if (transientFailure) throw new TransientCatalogException(failure);
            throw failure;
        }
    }
}
