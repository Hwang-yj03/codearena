package com.codearena.api.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.submission.dto.SubmissionResponse;
import com.codearena.api.submission.dto.SubmitRequest;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SubmissionService submissionService;

    private static SubmissionResponse pending(long id) {
        return new SubmissionResponse(id, 1000L, "cpp", "PENDING", null, null, null, null, null, null,
                LocalDateTime.now(), null);
    }

    @Test
    void submitReturns202WithLocation() throws Exception {
        given(submissionService.submit(any(SubmitRequest.class))).willReturn(pending(7));

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId": 1000, "language": "cpp", "source": "int main(){}"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/submissions/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unsupportedLanguageIs400() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId": 1000, "language": "rust", "source": "fn main(){}"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.language").exists());
    }

    @Test
    void blankSourceIs400() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId": 1000, "language": "cpp", "source": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.source").exists());
    }

    @Test
    void getReturnsSubmission() throws Exception {
        var done = new SubmissionResponse(7L, 1000L, "cpp", "DONE", "AC", 3, 3500, 3, 3, null,
                LocalDateTime.now(), LocalDateTime.now());
        given(submissionService.getSubmission(7L)).willReturn(done);

        mockMvc.perform(get("/api/submissions/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("AC"))
                .andExpect(jsonPath("$.passed").value(3));
    }

    @Test
    void getUnknownIs404() throws Exception {
        given(submissionService.getSubmission(99L)).willThrow(new NotFoundException("submission 99 not found"));

        mockMvc.perform(get("/api/submissions/99"))
                .andExpect(status().isNotFound());
    }
}
