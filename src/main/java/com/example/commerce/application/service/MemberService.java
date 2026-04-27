package com.example.commerce.application.service;

import com.example.commerce.domain.member.Member;
import com.example.commerce.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member create(String email) {
        return memberRepository.save(Member.of(email));
    }
}
