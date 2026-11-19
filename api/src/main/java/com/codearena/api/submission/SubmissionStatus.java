package com.codearena.api.submission;

public enum SubmissionStatus {
    /** 저장되어 채점 큐에 들어간 상태 */
    PENDING,
    /** 워커가 결과를 돌려준 상태 (verdict 확정) */
    DONE
}
