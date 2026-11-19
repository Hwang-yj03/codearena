package com.codearena.api.member;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codearena.api.config.SecurityConfig;
import com.codearena.api.member.dto.MemberResponse;
import com.codearena.api.submission.SubmissionService;
import com.codearena.api.submission.dto.SubmissionResponse;

@Import(SecurityConfig.class)
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberService memberService;

    @MockitoBean
    SubmissionService submissionService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentMember() throws Exception {
        given(memberService.getMember(3L))
                .willReturn(new MemberResponse(3L, "user@example.com", "tester", "USER", LocalDateTime.now()));

        mockMvc.perform(get("/api/members/me").with(jwt().jwt(j -> j.subject("3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nickname").value("tester"));
    }

    @Test
    void mySubmissionsUsesTokenSubject() throws Exception {
        var item = new SubmissionResponse(10L, 3L, 1000L, "cpp", "DONE", "AC", 1, 3500, 3, 3, null,
                LocalDateTime.now(), LocalDateTime.now());
        given(submissionService.getMySubmissions(3L, 0, 20))
                .willReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/members/me/submissions").with(jwt().jwt(j -> j.subject("3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].verdict").value("AC"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
