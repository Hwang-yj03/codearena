package com.codearena.api.member;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codearena.api.auth.CurrentMember;
import com.codearena.api.common.PageResponse;
import com.codearena.api.member.dto.MemberResponse;
import com.codearena.api.submission.SubmissionService;
import com.codearena.api.submission.dto.SubmissionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final SubmissionService submissionService;

    @GetMapping
    public MemberResponse me(@AuthenticationPrincipal Jwt jwt) {
        return memberService.getMember(CurrentMember.id(jwt));
    }

    @GetMapping("/submissions")
    public PageResponse<SubmissionResponse> mySubmissions(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(submissionService.getMySubmissions(CurrentMember.id(jwt), page, size));
    }
}
