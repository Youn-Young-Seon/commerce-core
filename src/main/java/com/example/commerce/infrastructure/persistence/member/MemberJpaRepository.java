package com.example.commerce.infrastructure.persistence.member;

import com.example.commerce.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
}
