package com.baklibra26.common.configuration;

import com.baklibra26.common.exception.GlobalExceptionHandler;
import com.baklibra26.job.JobService;
import com.baklibra26.job.controllers.JobControllerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("WebConfiguration")
@WebMvcTest(JobControllerImpl.class)
@Import({WebConfiguration.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "img-proc.cors.allowed-origins=http://localhost:3000",
        "img-proc.cors.allowed-methods=GET,POST,OPTIONS",
        "img-proc.cors.allowed-headers=Content-Type,Idempotency-Key",
        "img-proc.cors.max-age=1h"
})
class WebConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @Test
    @DisplayName("허용된 origin은 Idempotency-Key preflight 요청을 통과한다")
    void cors_allowsConfiguredOriginAndIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(options("/api/v1/jobs")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Headers", "Idempotency-Key"));
    }

    @Test
    @DisplayName("허용되지 않은 origin은 preflight 요청을 통과하지 못한다")
    void cors_rejectsNotConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/jobs")
                        .header("Origin", "https://not-allowed.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key"))
                .andExpect(status().isForbidden());
    }
}
