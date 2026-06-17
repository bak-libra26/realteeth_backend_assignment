package com.baklibra26.mockworker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockWorkerPayloads")
class MockWorkerPayloadsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("ProcessRequest는 imageUrl 필드로 직렬화된다")
    void processRequest_serializesImageUrl() throws Exception {
        // given
        MockWorkerPayloads.ProcessRequest request = new MockWorkerPayloads.ProcessRequest("https://example.com/image.png");

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertThat(json).contains("\"imageUrl\":\"https://example.com/image.png\"");
    }

    @Test
    @DisplayName("ProcessStartResponse는 worker job ID와 상태를 역직렬화한다")
    void processStartResponse_deserializesStatus() throws Exception {
        // when
        MockWorkerPayloads.ProcessStartResponse response = objectMapper.readValue(
                """
                {"jobId":"worker-1","status":"PROCESSING"}
                """,
                MockWorkerPayloads.ProcessStartResponse.class
        );

        // then
        assertThat(response.jobId()).isEqualTo("worker-1");
        assertThat(response.status()).isEqualTo(MockWorkerJobStatus.PROCESSING);
    }

    @Test
    @DisplayName("ProcessStartResponse 필수 필드를 검증한다")
    void processStartResponse_validatesRequiredFields() {
        // given
        MockWorkerPayloads.ProcessStartResponse response = new MockWorkerPayloads.ProcessStartResponse("", null);

        // when & then
        assertThat(validator.validate(response))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("jobId", "status");
    }

    @Test
    @DisplayName("ProcessStatusResponse는 처리 결과를 역직렬화한다")
    void processStatusResponse_deserializesResult() throws Exception {
        // when
        MockWorkerPayloads.ProcessStatusResponse response = objectMapper.readValue(
                """
                {"jobId":"worker-1","status":"COMPLETED","result":"done"}
                """,
                MockWorkerPayloads.ProcessStatusResponse.class
        );

        // then
        assertThat(response.jobId()).isEqualTo("worker-1");
        assertThat(response.status()).isEqualTo(MockWorkerJobStatus.COMPLETED);
        assertThat(response.result()).isEqualTo("done");
    }

    @Test
    @DisplayName("에러 detail이 비어 있으면 기본 메시지를 반환한다")
    void errorResponse_returnsFallbackMessage_whenDetailIsBlank() {
        // given
        MockWorkerErrorPayloads.ErrorResponse response = new MockWorkerErrorPayloads.ErrorResponse("");

        // when & then
        assertThat(response.message()).isEqualTo("Mock Worker request failed");
    }

    @Test
    @DisplayName("검증 오류 응답은 첫 번째 검증 메시지를 반환한다")
    void httpValidationError_returnsFirstValidationMessage() {
        // given
        MockWorkerErrorPayloads.ValidationError validationError = new MockWorkerErrorPayloads.ValidationError(
                List.of("body", "imageUrl"),
                "imageUrl is invalid",
                "value_error",
                null,
                Map.of()
        );

        MockWorkerErrorPayloads.HttpValidationError error = new MockWorkerErrorPayloads.HttpValidationError(
                List.of(validationError)
        );

        // when & then
        assertThat(error.message()).isEqualTo("imageUrl is invalid");
    }
}
