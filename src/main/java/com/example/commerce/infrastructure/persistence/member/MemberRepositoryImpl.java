package com.example.commerce.infrastructure.persistence.member;

import com.example.commerce.domain.member.Member;
import com.example.commerce.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    @Override
    public Optional<Member> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Member save(Member member) {
        return jpaRepository.save(member);
    }
}
