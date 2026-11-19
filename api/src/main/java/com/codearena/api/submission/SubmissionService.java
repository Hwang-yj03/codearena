package com.codearena.api.submission;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.judge.JudgeResultMessage;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.submission.dto.SubmissionResponse;
import com.codearena.api.submission.dto.SubmitRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 제출을 저장하고 이벤트를 발행한다. 큐 전송은 SubmissionQueuePublisher가
     * 트랜잭션 커밋 "이후"에 수행한다 (커밋 전에 보내면 워커 결과가 먼저 도착해 행을 못 찾을 수 있다).
     */
    @Transactional
    public SubmissionResponse submit(SubmitRequest request) {
        if (!problemRepository.existsById(request.problemId())) {
            throw new NotFoundException("problem " + request.problemId() + " not found");
        }
        Language language = Language.fromKey(request.language());
        Submission saved = submissionRepository.save(
                new Submission(request.problemId(), language, request.source()));

        eventPublisher.publishEvent(
                new SubmissionCreatedEvent(saved.getId(), saved.getProblemId(), language, saved.getSource()));
        return SubmissionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(Long id) {
        return submissionRepository.findById(id)
                .map(SubmissionResponse::from)
                .orElseThrow(() -> new NotFoundException("submission " + id + " not found"));
    }

    @Transactional
    public void applyResult(JudgeResultMessage result) {
        submissionRepository.findById(result.submissionId()).ifPresentOrElse(
                submission -> {
                    Verdict verdict = parseVerdict(result.verdict());
                    boolean applied = submission.applyResult(verdict, result.timeMs(), result.memoryKb(),
                            result.passed(), result.total(), result.compileLog());
                    if (!applied) {
                        log.info("submission {} already judged, duplicate result ignored", submission.getId());
                    }
                },
                () -> log.warn("result for unknown submission {} ignored", result.submissionId()));
    }

    private static Verdict parseVerdict(String raw) {
        try {
            return Verdict.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Verdict.SE;
        }
    }
}
