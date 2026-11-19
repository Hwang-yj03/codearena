package com.codearena.api.submission.dto;

import java.time.LocalDateTime;

import com.codearena.api.submission.Submission;

/** 소스 코드는 응답에 포함하지 않는다 (목록/결과 조회에 불필요하고 크기가 크다). */
public record SubmissionResponse(
        Long id,
        Long memberId,
        Long problemId,
        String language,
        String status,
        String verdict,
        Integer timeMs,
        Integer memoryKb,
        Integer passed,
        Integer total,
        String compileLog,
        LocalDateTime createdAt,
        LocalDateTime judgedAt
) {

    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(
                s.getId(), s.getMemberId(), s.getProblemId(), s.getLanguage().key(), s.getStatus().name(),
                s.getVerdict() == null ? null : s.getVerdict().name(),
                s.getTimeMs(), s.getMemoryKb(), s.getPassed(), s.getTotal(), s.getCompileLog(),
                s.getCreatedAt(), s.getJudgedAt());
    }
}
