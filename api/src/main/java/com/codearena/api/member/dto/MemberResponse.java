package com.codearena.api.member.dto;

import java.time.LocalDateTime;

import com.codearena.api.member.Member;

public record MemberResponse(Long id, String email, String nickname, String role, LocalDateTime createdAt) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getEmail(), m.getNickname(), m.getRole().name(), m.getCreatedAt());
    }
}
