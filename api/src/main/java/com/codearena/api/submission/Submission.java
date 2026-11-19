package com.codearena.api.submission;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 도입 전 제출은 NULL */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    // Hibernate 6+는 MySQL에서 enum을 네이티브 ENUM 타입으로 매핑하려 해서
    // VARCHAR 컬럼과 스키마 검증이 충돌한다. VARCHAR로 명시한다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Language language;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Verdict verdict;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    private Integer passed;

    private Integer total;

    @Column(name = "compile_log", columnDefinition = "TEXT")
    private String compileLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    public Submission(Long memberId, Long problemId, Language language, String source) {
        this.memberId = memberId;
        this.problemId = problemId;
        this.language = language;
        this.source = source;
        this.status = SubmissionStatus.PENDING;
    }

    /**
     * 채점 결과 반영. 같은 결과가 두 번 와도(재전송, 워커 재시도) 결과가 바뀌지 않도록
     * 이미 완료된 제출은 무시한다.
     *
     * @return 실제로 반영했으면 true
     */
    public boolean applyResult(Verdict verdict, int timeMs, int memoryKb, int passed, int total,
                               String compileLog) {
        if (status == SubmissionStatus.DONE) {
            return false;
        }
        this.verdict = verdict;
        this.timeMs = timeMs;
        this.memoryKb = memoryKb;
        this.passed = passed;
        this.total = total;
        this.compileLog = (compileLog == null || compileLog.isBlank()) ? null : compileLog;
        this.status = SubmissionStatus.DONE;
        this.judgedAt = LocalDateTime.now();
        return true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
