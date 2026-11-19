package com.codearena.api.judge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 워커 → API. 테스트케이스별 상세(cases)는 아직 저장하지 않으므로 무시한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JudgeResultMessage(
        @JsonProperty("submission_id") Long submissionId,
        @JsonProperty("verdict") String verdict,
        @JsonProperty("time_ms") int timeMs,
        @JsonProperty("memory_kb") int memoryKb,
        @JsonProperty("passed") int passed,
        @JsonProperty("total") int total,
        @JsonProperty("compile_log") String compileLog
) {
}
