package com.baklibra26.common.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Configuration properties")
class ConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(validProperties());

    @Test
    @DisplayName("worker, mock worker, candidate, CORS 설정을 바인딩한다")
    void bindsConfigurationProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JobWorkerProperties.class).submit().concurrency()).isEqualTo(3);
            assertThat(context.getBean(JobWorkerProperties.class).poll().batchSize()).isEqualTo(20);
            assertThat(context.getBean(MockWorkerProperty.class).baseUrl()).isEqualTo("https://example.com/mock");
            assertThat(context.getBean(CandidateProperty.class).email()).isEqualTo("candidate@example.com");
            assertThat(context.getBean(CorsProperties.class).allowedHeaders()).contains("Idempotency-Key");
        });
    }

    @Test
    @DisplayName("submit concurrency가 1보다 작으면 설정 오류로 기동하지 않는다")
    void failsToBind_whenSubmitConcurrencyIsLessThanOne() {
        contextRunner
                .withPropertyValues("img-proc.job-worker.submit.concurrency=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Test
    @DisplayName("candidate email 형식이 잘못되면 설정 오류로 기동하지 않는다")
    void failsToBind_whenCandidateEmailIsInvalid() {
        contextRunner
                .withPropertyValues("img-proc.candidate.email=invalid-email")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    private String[] validProperties() {
        return new String[] {
                "img-proc.cors.allowed-origins=http://localhost:3000",
                "img-proc.cors.allowed-methods=GET,POST,OPTIONS",
                "img-proc.cors.allowed-headers=Content-Type,Idempotency-Key",
                "img-proc.cors.max-age=1h",
                "img-proc.candidate.name=candidate",
                "img-proc.candidate.email=candidate@example.com",
                "img-proc.mock-worker.base-url=https://example.com/mock",
                "img-proc.mock-worker.api-key-issue.interval=1h",
                "img-proc.mock-worker.http.connect-timeout=1s",
                "img-proc.mock-worker.http.read-timeout=30s",
                "img-proc.job-worker.submit.interval=5s",
                "img-proc.job-worker.submit.batch-size=10",
                "img-proc.job-worker.submit.concurrency=3",
                "img-proc.job-worker.submit.lease-timeout=1m",
                "img-proc.job-worker.poll.interval=10s",
                "img-proc.job-worker.poll.batch-size=20",
                "img-proc.job-worker.poll.concurrency=5",
                "img-proc.job-worker.poll.lease-timeout=1m",
                "img-proc.job-worker.recovery.interval=30s"
        };
    }

    @Configuration
    @EnableConfigurationProperties({
            CandidateProperty.class,
            MockWorkerProperty.class,
            JobWorkerProperties.class,
            CorsProperties.class
    })
    static class PropertiesConfiguration {
    }
}
