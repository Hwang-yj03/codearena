package com.codearena.api.problem;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codearena.api.common.PageResponse;
import com.codearena.api.problem.dto.ProblemDetailResponse;
import com.codearena.api.problem.dto.ProblemSummary;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public PageResponse<ProblemSummary> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(problemService.getProblems(page, size));
    }

    @GetMapping("/{id}")
    public ProblemDetailResponse detail(@PathVariable Long id) {
        return problemService.getProblem(id);
    }
}
