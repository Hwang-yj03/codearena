package com.codearena.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codearena.api.health.HealthController;
import com.codearena.api.config.SecurityConfig;

/** DB 없이 웹 계층만 띄우는 슬라이스 테스트 */
@Import(SecurityConfig.class)
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    /** 실제 토큰 검증은 JwtTokenProviderTest에서 한다. 여기서는 jwt() 요청 후처리기로 인증을 흉내낸다. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingReturnsOk() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
