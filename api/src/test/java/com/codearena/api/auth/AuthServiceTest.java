package com.codearena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.codearena.api.auth.dto.LoginRequest;
import com.codearena.api.auth.dto.SignupRequest;
import com.codearena.api.common.ConflictException;
import com.codearena.api.common.UnauthorizedException;
import com.codearena.api.member.Member;
import com.codearena.api.member.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    JwtTokenProvider tokenProvider;

    PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(memberRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void signupStoresHashedPasswordAndNormalizedEmail() {
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        authService.signup(new SignupRequest("  User@Example.COM ", "password123", "tester"));

        var captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getPassword()).startsWith("{bcrypt}").doesNotContain("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void duplicateEmailIs409() {
        given(memberRepository.existsByEmail("user@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@example.com", "password123", "tester")))
                .isInstanceOf(ConflictException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void duplicateNicknameIs409() {
        given(memberRepository.existsByNickname("tester")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@example.com", "password123", "tester")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginIssuesToken() {
        Member member = new Member("user@example.com", passwordEncoder.encode("password123"), "tester");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
        given(tokenProvider.issueAccessToken(member)).willReturn("token-value");
        given(tokenProvider.accessTokenTtlSeconds()).willReturn(3600L);

        var token = authService.login(new LoginRequest("USER@example.com", "password123"));

        assertThat(token.accessToken()).isEqualTo("token-value");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameMessage() {
        Member member = new Member("user@example.com", passwordEncoder.encode("password123"), "tester");
        given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
        given(memberRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.LOGIN_FAILED);
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.LOGIN_FAILED);
    }
}
