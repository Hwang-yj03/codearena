package com.codearena.api.submission;

/** 제출이 DB에 커밋된 뒤 채점 큐로 보내기 위한 도메인 이벤트 */
public record SubmissionCreatedEvent(Long submissionId, Long problemId, Language language, String source) {
}
