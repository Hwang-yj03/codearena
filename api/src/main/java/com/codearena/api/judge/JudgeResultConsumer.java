package com.codearena.api.judge;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.codearena.api.submission.SubmissionService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결과 큐(codearena:results)를 BRPOP으로 소비해 DB에 반영하는 백그라운드 스레드.
 * SmartLifecycle로 만들어 애플리케이션 시작/종료에 맞춰 같이 켜지고 꺼진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "codearena.judge.result-consumer-enabled", havingValue = "true", matchIfMissing = true)
public class JudgeResultConsumer implements SmartLifecycle {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ERROR_BACKOFF = Duration.ofSeconds(3);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final SubmissionService submissionService;

    private volatile boolean running;
    private Thread worker;

    public JudgeResultConsumer(StringRedisTemplate redis, JsonMapper jsonMapper, SubmissionService submissionService) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.submissionService = submissionService;
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("judge-result-consumer").daemon(true).start(this::loop);
        log.info("judge result consumer started ({})", JudgeQueue.RESULTS);
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(POLL_TIMEOUT.toMillis() + 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("judge result consumer stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                String raw = redis.opsForList().rightPop(JudgeQueue.RESULTS, POLL_TIMEOUT);
                if (raw != null) {
                    handle(raw);
                }
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                log.warn("result consumer error, retrying in {}s: {}", ERROR_BACKOFF.toSeconds(), e.getMessage());
                sleepQuietly(ERROR_BACKOFF);
            }
        }
    }

    /** 메시지 한 건 처리. 형식이 깨진 메시지는 버리고 다음 메시지를 계속 처리한다. */
    void handle(String raw) {
        JudgeResultMessage result;
        try {
            result = jsonMapper.readValue(raw, JudgeResultMessage.class);
        } catch (JacksonException e) {
            log.error("invalid result message dropped: {}", raw.length() > 200 ? raw.substring(0, 200) : raw);
            return;
        }
        if (result.submissionId() == null) {
            log.error("result message without submission_id dropped");
            return;
        }
        submissionService.applyResult(result);
        log.info("submission {} -> {}", result.submissionId(), result.verdict());
    }

    private static void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
