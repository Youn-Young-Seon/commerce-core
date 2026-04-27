package com.example.commerce.domain.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 포인트 이벤트 로그.
 *
 * <p>잔액을 직접 수정하는 대신 모든 변동을 이력으로 누적합니다.
 * 잔액은 Member.pointBalance에 반영하되, 이력이 감사(audit) 근거가 됩니다.
 * 주문 취소 시 USE 이력을 찾아 CANCEL 이력을 생성하는 보상 트랜잭션을 수행합니다.
 */
@Entity
@Table(name = "point_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointHistoryType type;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static PointHistory earn(Long memberId, Long orderId, int amount) {
        return create(memberId, orderId, PointHistoryType.EARN, amount);
    }

    public static PointHistory use(Long memberId, Long orderId, int amount) {
        return create(memberId, orderId, PointHistoryType.USE, amount);
    }

    public static PointHistory cancel(Long memberId, Long orderId, int amount) {
        return create(memberId, orderId, PointHistoryType.CANCEL, amount);
    }

    private static PointHistory create(Long memberId, Long orderId, PointHistoryType type, int amount) {
        PointHistory history = new PointHistory();
        history.memberId = memberId;
        history.orderId = orderId;
        history.type = type;
        history.amount = amount;
        history.createdAt = LocalDateTime.now();
        return history;
    }
}
