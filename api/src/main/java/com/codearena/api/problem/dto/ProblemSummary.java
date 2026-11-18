package com.codearena.api.problem.dto;

import com.codearena.api.problem.Problem;

/** 목록 화면용. 지문과 예제는 내려주지 않는다. */
public record ProblemSummary(Long id, String title, int timeLimitMs, int memoryLimitMb) {

    public static ProblemSummary from(Problem p) {
        return new ProblemSummary(p.getId(), p.getTitle(), p.getTimeLimitMs(), p.getMemoryLimitMb());
    }
}
