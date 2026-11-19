package com.codearena.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codearena.api.auth.dto.LoginRequest;
import com.codearena.api.auth.dto.SignupRequest;
import com.codearena.api.auth.dto.TokenResponse;
import com.codearena.api.common.ConflictException;
import com.codearena.api.common.UnauthorizedException;
import com.codearena.api.config.SecurityConfig;
import com.codearena.api.member.dto.MemberResponse;

@Import(SecurityConfig.class)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void signupReturns201WithoutPassword() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
                .willReturn(new MemberResponse(1L, "user@example.com", "tester", "USER", LocalDateTime.now()));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "password123", "nickname": "tester"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void invalidSignupIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "nickname": "t"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.nickname").exists());
    }

    @Test
    void duplicateSignupIs409() throws Exception {
        given(authService.signup(any(SignupRequest.class))).willThrow(new ConflictException("이미 가입된 이메일입니다"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "password123", "nickname": "tester"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        given(authService.login(any(LoginRequest.class))).willReturn(TokenResponse.bearer("abc.def.ghi", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("abc.def.ghi"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void wrongCredentialsIs401() throws Exception {
        given(authService.login(any(LoginRequest.class))).willThrow(new UnauthorizedException(AuthService.LOGIN_FAILED));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
