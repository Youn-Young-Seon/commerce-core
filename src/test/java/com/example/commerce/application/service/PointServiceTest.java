package com.example.commerce.application.service;

import com.example.commerce.domain.member.Member;
import com.example.commerce.domain.point.PointHistory;
import com.example.commerce.domain.point.PointHistoryRepository;
import com.example.commerce.domain.point.PointHistoryType;
import com.example.commerce.domain.point.exception.InsufficientPointException;
import com.example.commerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointServiceTest extends IntegrationTestSupport {

    @Autowired
    private PointService pointService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Test
    @DisplayName("포인트 적립 후 사용하면 잔액이 정확히 차감되고 이력이 생성된다")
    void 포인트_적립_후_사용하면_잔액이_차감되고_이력이_생성된다() {
        // given
        Member member = memberService.create("earn-use@test.com");
        Long orderId = 1001L;

        // when
        pointService.earn(member.getId(), orderId, 10_000);
        pointService.use(member.getId(), orderId, 3_000);

        // then
        Member updated = pointService.getMember(member.getId());
        assertThat(updated.getPointBalance()).isEqualTo(7_000);

        Optional<PointHistory> useHistory = pointHistoryRepository
                .findByOrderIdAndType(orderId, PointHistoryType.USE);
        assertThat(useHistory).isPresent();
        assertThat(useHistory.get().getAmount()).isEqualTo(3_000);
    }

    @Test
    @DisplayName("주문 취소 시 보상 트랜잭션으로 포인트가 복구되고 CANCEL 이력이 생성된다")
    void 주문취소_시_보상트랜잭션으로_포인트가_복구된다() {
        // given
        Member member = memberService.create("cancel@test.com");
        Long orderId = 2001L;
        pointService.earn(member.getId(), orderId, 10_000);
        pointService.use(member.getId(), orderId, 5_000);

        // when
        pointService.cancel(orderId);

        // then
        Member updated = pointService.getMember(member.getId());
        assertThat(updated.getPointBalance()).isEqualTo(10_000); // 차감분 복구

        Optional<PointHistory> cancelHistory = pointHistoryRepository
                .findByOrderIdAndType(orderId, PointHistoryType.CANCEL);
        assertThat(cancelHistory).isPresent();
        assertThat(cancelHistory.get().getAmount()).isEqualTo(5_000);
        assertThat(cancelHistory.get().getType()).isEqualTo(PointHistoryType.CANCEL);
    }

    @Test
    @DisplayName("포인트를 사용하지 않은 주문 취소는 멱등적으로 처리된다")
    void 포인트_미사용_주문취소는_멱등적으로_처리된다() {
        // given
        Member member = memberService.create("no-point@test.com");
        Long orderId = 3001L;
        pointService.earn(member.getId(), orderId, 5_000);
        // 포인트 사용 없이 취소

        // when & then (예외 없이 처리)
        pointService.cancel(orderId);

        Member updated = pointService.getMember(member.getId());
        assertThat(updated.getPointBalance()).isEqualTo(5_000); // 변화 없음
    }

    @Test
    @DisplayName("잔액보다 많은 포인트를 사용하면 InsufficientPointException이 발생한다")
    void 잔액초과_포인트_사용시_예외가_발생한다() {
        // given
        Member member = memberService.create("insufficient@test.com");
        pointService.earn(member.getId(), 4001L, 1_000);

        // when & then
        assertThatThrownBy(() -> pointService.use(member.getId(), 4002L, 2_000))
                .isInstanceOf(InsufficientPointException.class);

        // 잔액은 그대로 (트랜잭션 롤백)
        Member updated = pointService.getMember(member.getId());
        assertThat(updated.getPointBalance()).isEqualTo(1_000);
    }
}
