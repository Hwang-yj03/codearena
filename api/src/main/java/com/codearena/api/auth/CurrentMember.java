package com.codearena.api.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.codearena.api.common.UnauthorizedException;

/** JWT의 sub(회원 id)를 꺼내는 헬퍼 */
public final class CurrentMember {

    private CurrentMember() {
    }

    public static Long id(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new UnauthorizedException("로그인이 필요합니다");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }
    }
}
