package com.codearena.api.problem;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.problem.dto.ProblemDetailResponse;
import com.codearena.api.problem.dto.ProblemSummary;

@WebMvcTest(ProblemController.class)
class ProblemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProblemService problemService;

    @Test
    void listReturnsPage() throws Exception {
        var content = List.of(new ProblemSummary(1000L, "A+B", 1000, 128));
        given(problemService.getProblems(0, 20)).willReturn(new PageImpl<>(content, PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1000))
                .andExpect(jsonPath("$.content[0].title").value("A+B"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void detailReturnsProblemWithSamples() throws Exception {
        var detail = new ProblemDetailResponse(1000L, "A+B", "desc", "in", "out", 1000, 128,
                List.of(new ProblemDetailResponse.Sample(1, "1 2", "3")));
        given(problemService.getProblem(1000L)).willReturn(detail);

        mockMvc.perform(get("/api/problems/1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A+B"))
                .andExpect(jsonPath("$.samples[0].input").value("1 2"))
                .andExpect(jsonPath("$.samples[0].output").value("3"));
    }

    @Test
    void detailReturns404ProblemDetail() throws Exception {
        given(problemService.getProblem(9999L)).willThrow(new NotFoundException("problem 9999 not found"));

        mockMvc.perform(get("/api/problems/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("problem 9999 not found"));
    }
}
