package com.codearena.api.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.judge.JudgeResultMessage;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.submission.dto.SubmitRequest;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    SubmissionRepository submissionRepository;

    @Mock
    ProblemRepository problemRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    SubmissionService submissionService;

    @Test
    void submitSavesPendingSubmissionAndPublishesEvent() {
        given(problemRepository.existsById(1000L)).willReturn(true);
        given(submissionRepository.save(any(Submission.class))).willAnswer(inv -> {
            Submission s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            return s;
        });

        var response = submissionService.submit(new SubmitRequest(1000L, "python", "print(1)"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.language()).isEqualTo("python");

        var captor = ArgumentCaptor.forClass(SubmissionCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().submissionId()).isEqualTo(1L);
        assertThat(captor.getValue().language()).isEqualTo(Language.PYTHON);
    }

    @Test
    void submitToUnknownProblemFailsWithoutSavingOrPublishing() {
        given(problemRepository.existsById(9999L)).willReturn(false);

        assertThatThrownBy(() -> submissionService.submit(new SubmitRequest(9999L, "cpp", "x")))
                .isInstanceOf(NotFoundException.class);
        verify(submissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void applyResultUpdatesSubmission() {
        var s = new Submission(1000L, Language.CPP, "src");
        given(submissionRepository.findById(1L)).willReturn(Optional.of(s));

        submissionService.applyResult(new JudgeResultMessage(1L, "WA", 2, 3500, 2, 3, ""));

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(s.getVerdict()).isEqualTo(Verdict.WA);
        assertThat(s.getPassed()).isEqualTo(2);
    }

    @Test
    void unknownVerdictBecomesSystemError() {
        var s = new Submission(1000L, Language.CPP, "src");
        given(submissionRepository.findById(1L)).willReturn(Optional.of(s));

        submissionService.applyResult(new JudgeResultMessage(1L, "WHAT", 0, 0, 0, 3, null));

        assertThat(s.getVerdict()).isEqualTo(Verdict.SE);
    }

    @Test
    void resultForUnknownSubmissionIsIgnored() {
        given(submissionRepository.findById(42L)).willReturn(Optional.empty());

        submissionService.applyResult(new JudgeResultMessage(42L, "AC", 1, 1, 3, 3, null));
        // 예외 없이 끝나면 통과
    }
}
