package com.codearena.api.judge;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.codearena.api.submission.SubmissionCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionQueuePublisher {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    /**
     * 트랜잭션이 커밋된 뒤에만 큐에 넣는다.
     * - 커밋 전에 넣으면: 워커가 아주 빨리 채점해 결과를 보냈을 때 DB에 아직 행이 없을 수 있다.
     * - 롤백되면: 존재하지 않는 제출이 채점되는 일을 막는다.
     *
     * 한계: 커밋 후 Redis 전송이 실패하면 제출은 PENDING으로 남는다. (docs/03-api-submission.md 참고)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        var message = new JudgeRequestMessage(event.submissionId(), String.valueOf(event.problemId()),
                event.language().key(), event.source());
        try {
            redis.opsForList().leftPush(JudgeQueue.SUBMISSIONS, jsonMapper.writeValueAsString(message));
            log.info("submission {} enqueued", event.submissionId());
        } catch (RuntimeException e) {
            log.error("failed to enqueue submission {} (stays PENDING)", event.submissionId(), e);
        }
    }
}
