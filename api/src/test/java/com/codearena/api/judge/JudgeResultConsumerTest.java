package com.codearena.api.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.codearena.api.submission.SubmissionService;

import tools.jackson.databind.json.JsonMapper;

class JudgeResultConsumerTest {

    SubmissionService submissionService;
    JudgeResultConsumer consumer;

    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        consumer = new JudgeResultConsumer(mock(StringRedisTemplate.class), JsonMapper.builder().build(),
                submissionService);
    }

    @Test
    void parsesWorkerResultFormat() {
        // judge-worker가 실제로 보내는 형식 그대로 (cases 포함)
        consumer.handle("""
                {"submission_id": 5, "verdict": "WA", "time_ms": 1, "memory_kb": 3664,
                 "passed": 2, "total": 3, "compile_log": "",
                 "cases": [{"index": 1, "verdict": "AC", "time_ms": 1, "memory_kb": 3600}]}
                """);

        var captor = ArgumentCaptor.forClass(JudgeResultMessage.class);
        verify(submissionService).applyResult(captor.capture());
        var r = captor.getValue();
        assertThat(r.submissionId()).isEqualTo(5L);
        assertThat(r.verdict()).isEqualTo("WA");
        assertThat(r.memoryKb()).isEqualTo(3664);
        assertThat(r.passed()).isEqualTo(2);
    }

    @Test
    void brokenJsonIsDropped() {
        consumer.handle("{not json");

        verify(submissionService, never()).applyResult(any());
    }

    @Test
    void messageWithoutSubmissionIdIsDropped() {
        consumer.handle("""
                {"verdict": "AC", "time_ms": 1, "memory_kb": 1, "passed": 1, "total": 1}
                """);

        verify(submissionService, never()).applyResult(any());
    }
}
