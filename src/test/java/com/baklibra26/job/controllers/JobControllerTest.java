package com.baklibra26.job.controllers;

import com.baklibra26.common.exception.GlobalExceptionHandler;
import com.baklibra26.job.Job;
import com.baklibra26.job.JobPayloads;
import com.baklibra26.job.JobService;
import com.baklibra26.job.exceptions.IdempotencyConflictException;
import com.baklibra26.job.exceptions.JobNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("JobController")
@WebMvcTest(JobControllerImpl.class)
@Import(GlobalExceptionHandler.class)
class JobControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("작업 생성 요청이 성공하면 202와 작업 정보를 반환한다")
    void createJob_returnsAcceptedJob() throws Exception {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        when(jobService.createJob("key-1", "https://example.com/image.png")).thenReturn(job);

        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("https://example.com/image.png"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/image.png"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("멱등키 헤더가 없으면 400을 반환하고 작업을 생성하지 않는다")
    void createJob_returnsBadRequest_whenIdempotencyKeyHeaderIsMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("https://example.com/image.png"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("멱등키 헤더가 공백이면 400을 반환하고 작업을 생성하지 않는다")
    void createJob_returnsBadRequest_whenIdempotencyKeyIsBlank() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("https://example.com/image.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("멱등키 헤더가 너무 길면 400을 반환하고 작업을 생성하지 않는다")
    void createJob_returnsBadRequest_whenIdempotencyKeyIsTooLong() throws Exception {
        // given
        String tooLongKey = "a".repeat(129);

        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", tooLongKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("https://example.com/image.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("이미지 URL이 유효하지 않으면 400을 반환한다")
    void createJob_returnsBadRequest_whenImageUrlIsInvalid() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("http://example.com/image.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("멱등키 충돌이 발생하면 409를 반환한다")
    void createJob_returnsConflict_whenIdempotencyKeyConflicts() throws Exception {
        // given
        when(jobService.createJob(eq("key-1"), any()))
                .thenThrow(new IdempotencyConflictException("key-1"));

        // when & then
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobPayloads.CreateJobRequest("https://example.com/image.png"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("작업 ID로 작업을 조회하면 200과 작업 정보를 반환한다")
    void getJob_returnsJob() throws Exception {
        // given
        UUID jobId = UUID.randomUUID();
        Job job = new Job("key-1", "https://example.com/image.png");
        when(jobService.getJobById(jobId)).thenReturn(job);

        // when & then
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("없는 작업 ID를 조회하면 404를 반환한다")
    void getJob_returnsNotFound_whenJobDoesNotExist() throws Exception {
        // given
        UUID jobId = UUID.randomUUID();
        when(jobService.getJobById(jobId)).thenThrow(new JobNotFoundException(jobId));

        // when & then
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("작업 목록을 조회하면 200과 작업 목록을 반환한다")
    void getJobs_returnsJobs() throws Exception {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        when(jobService.getJobs(0, 20)).thenReturn(
                new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1)
        );

        // when & then
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(jobService).getJobs(0, 20);
    }

    @Test
    @DisplayName("작업 목록 조회는 page와 size를 적용한다")
    void getJobs_appliesPageAndSize() throws Exception {
        // given
        when(jobService.getJobs(2, 5)).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(2, 5), 20)
        );

        // when & then
        mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.hasNext").value(true));
        verify(jobService).getJobs(2, 5);
    }

    @Test
    @DisplayName("작업 목록 조회 page가 음수이면 400을 반환한다")
    void getJobs_returnsBadRequest_whenPageIsNegative() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("작업 목록 조회 size가 1보다 작으면 400을 반환한다")
    void getJobs_returnsBadRequest_whenSizeIsTooSmall() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("작업 목록 조회 size가 너무 크면 400을 반환한다")
    void getJobs_returnsBadRequest_whenSizeIsTooLarge() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
