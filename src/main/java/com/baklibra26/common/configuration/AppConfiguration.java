package com.baklibra26.common.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties({
    CandidateProperty.class, MockWorkerProperty.class, JobWorkerProperties.class
})
public class AppConfiguration {

    @Bean
    public RestClient mockWorkerRestClient(MockWorkerProperty property) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(property.http().connectTimeout());
        requestFactory.setReadTimeout(property.http().readTimeout());

        return RestClient.builder()
                .baseUrl(property.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Executor jobSubmitExecutor(JobWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.submit().concurrency());
        executor.setMaxPoolSize(properties.submit().concurrency());
        executor.setQueueCapacity(properties.submit().batchSize());
        executor.setThreadNamePrefix("job-submit-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor jobPollExecutor(JobWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poll().concurrency());
        executor.setMaxPoolSize(properties.poll().concurrency());
        executor.setQueueCapacity(properties.poll().batchSize());
        executor.setThreadNamePrefix("job-poll-");
        executor.initialize();
        return executor;
    }


}
