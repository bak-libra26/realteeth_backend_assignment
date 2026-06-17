package com.baklibra26.mockworker;

import com.baklibra26.job.Job;
import com.baklibra26.mockworker.MockWorkerPayloads.ProcessStartResponse;
import com.baklibra26.mockworker.MockWorkerPayloads.ProcessStatusResponse;
import com.baklibra26.mockworker.exceptions.MockWorkerException;
import com.baklibra26.mockworker.exceptions.MockWorkerHttpException;
import com.baklibra26.mockworker.exceptions.MockWorkerInvalidResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("MockWorkerClient")
class MockWorkerClientTest {

    private MockRestServiceServer server;
    private MockWorkerClient client;

    @BeforeEach
    void setUp() {
        // 실제 네트워크 대신 MockRestServiceServer로 외부 API 계약을 검증한다.
        RestClient.Builder builder = RestClient.builder().baseUrl("https://mock-worker.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MockWorkerClient(builder.build());
    }

    @Test
    @DisplayName("API key 발급 요청을 보내고 발급된 key를 반환한다")
    void issueApiKey_postsCandidateAndReturnsApiKey() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/auth/issue-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"candidateName":"candidate","email":"candidate@example.com"}
                        """))
                .andRespond(withSuccess("""
                        {"apiKey":"mock-api-key"}
                        """, MediaType.APPLICATION_JSON));

        // when
        String apiKey = client.issueApiKey("candidate", "candidate@example.com");

        // then
        assertThat(apiKey).isEqualTo("mock-api-key");
        server.verify();
    }

    @Test
    @DisplayName("API key 발급 응답이 비어 있으면 예외가 발생한다")
    void issueApiKey_throws_whenApiKeyIsEmpty() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/auth/issue-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"apiKey":""}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.issueApiKey("candidate", "candidate@example.com"))
                .isInstanceOf(MockWorkerException.class)
                .hasMessageContaining("API key issue response is empty");
    }

    @Test
    @DisplayName("작업 제출 시 이미지 URL과 API key를 보내고 worker job 정보를 반환한다")
    void submit_postsImageUrlAndReturnsWorkerJob() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-KEY", "mock-api-key"))
                .andExpect(content().json("""
                        {"imageUrl":"https://example.com/image.png"}
                        """))
                .andRespond(withSuccess("""
                        {"jobId":"worker-1","status":"PROCESSING"}
                        """, MediaType.APPLICATION_JSON));

        // when
        ProcessStartResponse response = client.submit(job, "mock-api-key");

        // then
        assertThat(response.jobId()).isEqualTo("worker-1");
        assertThat(response.status()).isEqualTo(MockWorkerJobStatus.PROCESSING);
        server.verify();
    }

    @Test
    @DisplayName("작업 제출이 422로 실패하면 MockWorkerHttpException으로 변환한다")
    void submit_throwsHttpException_whenMockWorkerReturnsValidationError() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail":[{"loc":["body","imageUrl"],"msg":"imageUrl is invalid","type":"value_error"}]}
                                """));

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOfSatisfying(MockWorkerHttpException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(422);
                    assertThat(e.getBody().message()).isEqualTo("imageUrl is invalid");
                });
    }

    @Test
    @DisplayName("작업 제출 응답 본문이 비어 있으면 invalid response 예외가 발생한다")
    void submit_throwsInvalidResponse_whenBodyIsEmpty() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("process start response is empty");
    }

    @Test
    @DisplayName("작업 제출 응답 처리 실패는 invalid response 예외로 변환한다")
    void submit_throwsInvalidResponse_whenResponseExtractionFails() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(request -> {
                    throw new RestClientException("response extraction failed");
                });

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("process start response could not be parsed");
    }

    @Test
    @DisplayName("작업 제출 응답에 worker job ID가 없으면 invalid response 예외가 발생한다")
    void submit_throwsInvalidResponse_whenJobIdIsEmpty() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(withSuccess("""
                        {"jobId":"","status":"PROCESSING"}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("jobId is empty");
    }

    @Test
    @DisplayName("작업 제출 응답에 status가 없으면 invalid response 예외가 발생한다")
    void submit_throwsInvalidResponse_whenStatusIsMissing() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(withSuccess("""
                        {"jobId":"worker-1"}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("status is empty");
    }

    @Test
    @DisplayName("작업 제출 응답 status가 PROCESSING이 아니면 invalid response 예외가 발생한다")
    void submit_throwsInvalidResponse_whenStatusIsNotProcessing() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        server.expect(once(), requestTo("https://mock-worker.test/process"))
                .andRespond(withSuccess("""
                        {"jobId":"worker-1","status":"FAILED"}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.submit(job, "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("status must be PROCESSING");
    }

    @Test
    @DisplayName("상태 조회 시 API key를 보내고 worker 상태를 반환한다")
    void getStatus_getsStatusWithApiKey() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/process/worker-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-KEY", "mock-api-key"))
                .andRespond(withSuccess("""
                        {"jobId":"worker-1","status":"COMPLETED","result":"done"}
                        """, MediaType.APPLICATION_JSON));

        // when
        ProcessStatusResponse response = client.getStatus("worker-1", "mock-api-key");

        // then
        assertThat(response.jobId()).isEqualTo("worker-1");
        assertThat(response.status()).isEqualTo(MockWorkerJobStatus.COMPLETED);
        assertThat(response.result()).isEqualTo("done");
        server.verify();
    }

    @Test
    @DisplayName("상태 조회 응답에 status가 없으면 invalid response 예외가 발생한다")
    void getStatus_throwsInvalidResponse_whenStatusIsMissing() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/process/worker-1"))
                .andRespond(withSuccess("""
                        {"jobId":"worker-1","result":"done"}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getStatus("worker-1", "mock-api-key"))
                .isInstanceOf(MockWorkerInvalidResponseException.class)
                .hasMessageContaining("status is empty");
    }

    @Test
    @DisplayName("상태 조회가 429로 실패하면 재시도 가능한 HTTP 예외로 변환한다")
    void getStatus_throwsHttpExceptionWithRetryableFlags() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/process/worker-1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail":"rate limited"}
                                """));

        // when & then
        assertThatThrownBy(() -> client.getStatus("worker-1", "mock-api-key"))
                .isInstanceOfSatisfying(MockWorkerHttpException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(429);
                    assertThat(e.isRateLimited()).isTrue();
                    assertThat(e.isClientError()).isTrue();
                    assertThat(e.isServerError()).isFalse();
                    assertThat(e.getBody().message()).isEqualTo("rate limited");
                });
    }

    @Test
    @DisplayName("Mock Worker 에러 응답 본문 파싱에 실패해도 HTTP 예외로 변환한다")
    void getStatus_throwsHttpException_whenErrorBodyIsInvalid() {
        // given
        server.expect(once(), requestTo("https://mock-worker.test/process/worker-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not-json"));

        // when & then
        assertThatThrownBy(() -> client.getStatus("worker-1", "mock-api-key"))
                .isInstanceOfSatisfying(MockWorkerHttpException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(500);
                    assertThat(e.isServerError()).isTrue();
                    assertThat(e.getBody().message()).isEqualTo("not-json");
                });
    }
}
