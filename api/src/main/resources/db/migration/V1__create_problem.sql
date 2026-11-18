CREATE TABLE problem (
    id                 BIGINT       NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT         NOT NULL,
    input_description  TEXT         NOT NULL,
    output_description TEXT         NOT NULL,
    time_limit_ms      INT          NOT NULL,
    memory_limit_mb    INT          NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE problem_sample (
    problem_id BIGINT NOT NULL,
    seq        INT    NOT NULL,
    input      TEXT   NOT NULL,
    output     TEXT   NOT NULL,
    PRIMARY KEY (problem_id, seq),
    CONSTRAINT fk_problem_sample_problem FOREIGN KEY (problem_id) REFERENCES problem (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
