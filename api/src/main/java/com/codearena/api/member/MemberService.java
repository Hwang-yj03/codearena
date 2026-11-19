package com.codearena.api.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codearena.api.common.NotFoundException;
import com.codearena.api.member.dto.MemberResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResponse getMember(Long id) {
        return memberRepository.findById(id)
                .map(MemberResponse::from)
                .orElseThrow(() -> new NotFoundException("member " + id + " not found"));
    }
}
