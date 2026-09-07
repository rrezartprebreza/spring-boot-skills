package com.example.verification;

import com.example.config.ClientProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ClientProperties.class)
    static class Config {}

    @Test void bindsDurationWithUnits() {
        runner.withPropertyValues("app.client.base-url=https://example.com", "app.client.timeout=2s", "app.client.max-attempts=3")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ClientProperties.class).timeout()).isEqualTo(Duration.ofSeconds(2));
            });
    }
    @ParameterizedTest @ValueSource(strings = {"0s", "-1s", "nonsense"})
    void rejectsInvalidDuration(String timeout) {
        runner.withPropertyValues("app.client.base-url=https://example.com", "app.client.timeout=" + timeout, "app.client.max-attempts=3")
            .run(context -> assertThat(context).hasFailed());
    }
    @Test void rejectsMissingRequiredProperties() {
        runner.run(context -> assertThat(context).hasFailed());
    }
    @Test void rejectsExcessiveRetries() {
        runner.withPropertyValues("app.client.base-url=https://example.com", "app.client.timeout=2s", "app.client.max-attempts=6")
            .run(context -> assertThat(context).hasFailed());
    }
}
