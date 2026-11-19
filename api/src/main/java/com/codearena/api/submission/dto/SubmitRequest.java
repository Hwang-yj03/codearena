package com.codearena.api.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitRequest(
        @NotNull Long problemId,
        @NotNull @Pattern(regexp = "cpp|python|java", message = "cpp, python, java 중 하나여야 합니다") String language,
        @NotBlank @Size(max = 65_536, message = "소스 코드는 64KB 이하여야 합니다") String source
) {
}
