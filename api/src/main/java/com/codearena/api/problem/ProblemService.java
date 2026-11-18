package com.codearena.api.problem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.problem.dto.ProblemDetailResponse;
import com.codearena.api.problem.dto.ProblemSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problemRepository;

    public Page<ProblemSummary> getProblems(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return problemRepository.findAll(PageRequest.of(safePage, safeSize, Sort.by("id")))
                .map(ProblemSummary::from);
    }

    /** open-in-view를 껐기 때문에 지연 로딩되는 samples는 트랜잭션 안에서 DTO로 변환한다. */
    public ProblemDetailResponse getProblem(Long id) {
        return problemRepository.findById(id)
                .map(ProblemDetailResponse::from)
                .orElseThrow(() -> new NotFoundException("problem " + id + " not found"));
    }
}
