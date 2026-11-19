package com.codearena.api.submission;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codearena.api.submission.dto.SubmissionResponse;
import com.codearena.api.submission.dto.SubmitRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** 채점은 비동기라 결과가 아니라 "접수됨(202)"을 돌려주고, 조회 URL을 Location에 담는다. */
    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(@Valid @RequestBody SubmitRequest request) {
        SubmissionResponse created = submissionService.submit(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/submissions/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public SubmissionResponse get(@PathVariable Long id) {
        return submissionService.getSubmission(id);
    }
}
