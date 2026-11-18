package com.codearena.api.problem.dto;

import java.util.List;

import com.codearena.api.problem.Problem;

public record ProblemDetailResponse(
        Long id,
        String title,
        String description,
        String inputDescription,
        String outputDescription,
        int timeLimitMs,
        int memoryLimitMb,
        List<Sample> samples
) {

    public record Sample(int seq, String input, String output) {
    }

    public static ProblemDetailResponse from(Problem p) {
        return new ProblemDetailResponse(
                p.getId(), p.getTitle(), p.getDescription(), p.getInputDescription(),
                p.getOutputDescription(), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                p.getSamples().stream().map(s -> new Sample(s.getSeq(), s.getInput(), s.getOutput())).toList());
    }
}
