package com.codearena.api.judge;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API → 워커. 워커가 Python이라 필드명은 snake_case로 맞춘다. */
public record JudgeRequestMessage(
        @JsonProperty("submission_id") Long submissionId,
        @JsonProperty("problem_id") String problemId,
        @JsonProperty("language") String language,
        @JsonProperty("source") String source
) {
}
