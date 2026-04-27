package com.example.commerce.presentation.dto.response;

import com.example.commerce.domain.point.PointHistory;
import com.example.commerce.domain.point.PointHistoryType;

import java.time.LocalDateTime;

public record PointHistoryResponse(
        Long id,
        Long memberId,
        Long orderId,
        PointHistoryType type,
        int amount,
        LocalDateTime createdAt
) {
    public static PointHistoryResponse from(PointHistory history) {
        return new PointHistoryResponse(
                history.getId(),
                history.getMemberId(),
                history.getOrderId(),
                history.getType(),
                history.getAmount(),
                history.getCreatedAt()
        );
    }
}
