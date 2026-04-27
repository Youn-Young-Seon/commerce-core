package com.example.commerce.domain.member;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(Long id);

    Member save(Member member);
}
