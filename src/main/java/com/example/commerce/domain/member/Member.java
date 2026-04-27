package com.example.commerce.domain.member;

import com.example.commerce.domain.point.exception.InsufficientPointException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int pointBalance;

    public static Member of(String email) {
        Member member = new Member();
        member.email = email;
        member.pointBalance = 0;
        return member;
    }

    public void deductPoint(int amount) {
        if (this.pointBalance < amount) {
            throw new InsufficientPointException();
        }
        this.pointBalance -= amount;
    }

    public void addPoint(int amount) {
        this.pointBalance += amount;
    }
}
