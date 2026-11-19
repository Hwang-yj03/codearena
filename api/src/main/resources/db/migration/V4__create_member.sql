CREATE TABLE member (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(100) NOT NULL,
    nickname   VARCHAR(20)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 기존(로그인 도입 전) 제출이 있으므로 NULL 허용
ALTER TABLE submission
    ADD COLUMN member_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_submission_member FOREIGN KEY (member_id) REFERENCES member (id),
    ADD INDEX idx_submission_member (member_id, id);
