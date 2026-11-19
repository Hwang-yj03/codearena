package com.codearena.api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codearena.api.auth.dto.LoginRequest;
import com.codearena.api.auth.dto.SignupRequest;
import com.codearena.api.auth.dto.TokenResponse;
import com.codearena.api.common.ConflictException;
import com.codearena.api.common.UnauthorizedException;
import com.codearena.api.member.Member;
import com.codearena.api.member.MemberRepository;
import com.codearena.api.member.dto.MemberResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 이메일 존재 여부를 응답 메시지로 구분하지 않는다 (계정 존재 여부 노출 방지) */
    static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        if (memberRepository.existsByEmail(email)) {
            throw new ConflictException("이미 가입된 이메일입니다");
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new ConflictException("이미 사용 중인 닉네임입니다");
        }
        Member saved = memberRepository.save(
                new Member(email, passwordEncoder.encode(request.password()), request.nickname()));
        return MemberResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED);
        }
        return TokenResponse.bearer(tokenProvider.issueAccessToken(member), tokenProvider.accessTokenTtlSeconds());
    }
}
