package com.example.commerce.presentation.dto.request;

public record PointRequest(
        Long memberId,
        Long orderId,
        int amount
) {
}
