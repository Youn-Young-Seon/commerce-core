package com.example.commerce.application.service;

import com.example.commerce.domain.member.Member;
import com.example.commerce.domain.member.MemberRepository;
import com.example.commerce.domain.member.exception.MemberNotFoundException;
import com.example.commerce.domain.point.PointHistory;
import com.example.commerce.domain.point.PointHistoryRepository;
import com.example.commerce.domain.point.PointHistoryType;
import com.example.commerce.domain.point.exception.PointHistoryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 포인트 사용.
     *
     * <p>잔액 차감과 USE 이력 저장을 같은 트랜잭션에서 처리합니다.
     * 부분 성공이 불가능하므로 데이터 일관성이 보장됩니다.
     */
    @Transactional
    public PointHistory use(Long memberId, Long orderId, int amount) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        member.deductPoint(amount); // InsufficientPointException 발생 가능
        memberRepository.save(member);

        return pointHistoryRepository.save(PointHistory.use(memberId, orderId, amount));
    }

    /**
     * 포인트 적립.
     */
    @Transactional
    public PointHistory earn(Long memberId, Long orderId, int amount) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        member.addPoint(amount);
        memberRepository.save(member);

        return pointHistoryRepository.save(PointHistory.earn(memberId, orderId, amount));
    }

    /**
     * 보상 트랜잭션 — 주문 취소 시 사용된 포인트 복구.
     *
     * <p>USE 이력을 찾아 동일 금액을 복구하고 CANCEL 이력을 생성합니다.
     * 포인트를 사용하지 않은 주문 취소라면 아무 작업도 하지 않습니다 (멱등성).
     */
    @Transactional
    public void cancel(Long orderId) {
        pointHistoryRepository.findByOrderIdAndType(orderId, PointHistoryType.USE)
                .ifPresent(usageHistory -> {
                    Member member = memberRepository.findById(usageHistory.getMemberId())
                            .orElseThrow(() -> new MemberNotFoundException(usageHistory.getMemberId()));

                    member.addPoint(usageHistory.getAmount());
                    memberRepository.save(member);

                    pointHistoryRepository.save(
                            PointHistory.cancel(usageHistory.getMemberId(), orderId, usageHistory.getAmount())
                    );
                });
    }

    @Transactional(readOnly = true)
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
