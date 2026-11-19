package com.codearena.api.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.codearena.api.member.Member;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    static final String ISSUER = "codearena";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public String issueAccessToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(String.valueOf(member.getId()))
                .claim("nickname", member.getNickname())
                // 기본 JwtAuthenticationConverter가 scope 클레임을 SCOPE_xxx 권한으로 바꿔준다
                .claim("scope", member.getRole().name())
                .build();
        // Spring Security 7의 디코더는 typ 헤더를 검증한다. 인코더 기본값에 기대지 않고 명시한다.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
