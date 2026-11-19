CREATE TABLE submission (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    problem_id  BIGINT      NOT NULL,
    language    VARCHAR(20) NOT NULL,
    source      MEDIUMTEXT  NOT NULL,
    status      VARCHAR(20) NOT NULL,
    verdict     VARCHAR(10) NULL,
    time_ms     INT         NULL,
    memory_kb   INT         NULL,
    passed      INT         NULL,
    total       INT         NULL,
    compile_log TEXT        NULL,
    created_at  DATETIME(6) NOT NULL,
    judged_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_submission_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    INDEX idx_submission_problem (problem_id, id),
    INDEX idx_submission_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
