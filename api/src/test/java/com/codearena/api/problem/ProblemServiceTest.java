package com.codearena.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.problem.dto.ProblemDetailResponse;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    ProblemRepository problemRepository;

    @InjectMocks
    ProblemService problemService;

    @Test
    void getProblemMapsSamples() {
        var problem = new Problem(1000L, "A+B", "desc", "in", "out", 1000, 128);
        problem.addSample("1 2", "3");
        problem.addSample("-5 5", "0");
        given(problemRepository.findById(1000L)).willReturn(Optional.of(problem));

        var detail = problemService.getProblem(1000L);

        assertThat(detail.title()).isEqualTo("A+B");
        assertThat(detail.samples()).extracting(ProblemDetailResponse.Sample::seq).containsExactly(1, 2);
    }

    @Test
    void getProblemThrowsWhenMissing() {
        given(problemRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> problemService.getProblem(9999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pageSizeIsClamped() {
        given(problemRepository.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        problemService.getProblems(-3, 10_000);

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(problemRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(ProblemService.MAX_PAGE_SIZE);
    }
}
