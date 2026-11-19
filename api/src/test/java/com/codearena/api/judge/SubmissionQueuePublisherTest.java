package com.codearena.api.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.codearena.api.submission.Language;
import com.codearena.api.submission.SubmissionCreatedEvent;

import tools.jackson.databind.json.JsonMapper;

class SubmissionQueuePublisherTest {

    @SuppressWarnings("unchecked")
    ListOperations<String, String> listOps = mock(ListOperations.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    SubmissionQueuePublisher publisher = new SubmissionQueuePublisher(redis, JsonMapper.builder().build());

    @Test
    void pushesSnakeCaseMessageThatWorkerUnderstands() {
        given(redis.opsForList()).willReturn(listOps);

        publisher.onSubmissionCreated(new SubmissionCreatedEvent(3L, 1000L, Language.JAVA, "class Main{}"));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(listOps).leftPush(eq(JudgeQueue.SUBMISSIONS), captor.capture());
        assertThat(captor.getValue())
                .contains("\"submission_id\":3")
                .contains("\"problem_id\":\"1000\"")
                .contains("\"language\":\"java\"");
    }

    @Test
    void redisFailureDoesNotPropagate() {
        given(redis.opsForList()).willReturn(listOps);
        given(listOps.leftPush(eq(JudgeQueue.SUBMISSIONS), anyString()))
                .willThrow(new RedisConnectionFailureException("down"));

        publisher.onSubmissionCreated(new SubmissionCreatedEvent(3L, 1000L, Language.CPP, "x"));
        // 예외가 밖으로 나오지 않으면 통과 (제출은 PENDING으로 남는다)
    }
}
