package com.baklibra26.common.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppConfiguration")
class AppConfigurationTest {

    @Test
    @DisplayName("submit executor의 thread pool 크기는 submit concurrency 설정을 따른다")
    void jobSubmitExecutor_usesSubmitConcurrency() {
        AppConfiguration configuration = new AppConfiguration();
        JobWorkerProperties properties = properties(3, 5);

        Executor executor = configuration.jobSubmitExecutor(properties);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(3);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(3);
        taskExecutor.shutdown();
    }

    @Test
    @DisplayName("poll executor의 thread pool 크기는 poll concurrency 설정을 따른다")
    void jobPollExecutor_usesPollConcurrency() {
        AppConfiguration configuration = new AppConfiguration();
        JobWorkerProperties properties = properties(3, 5);

        Executor executor = configuration.jobPollExecutor(properties);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(5);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(5);
        taskExecutor.shutdown();
    }

    private JobWorkerProperties properties(int submitConcurrency, int pollConcurrency) {
        return new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, submitConcurrency, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, pollConcurrency, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
    }
}
