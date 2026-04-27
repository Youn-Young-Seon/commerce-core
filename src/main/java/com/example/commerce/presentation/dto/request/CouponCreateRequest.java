package com.example.commerce.presentation.dto.request;

public record CouponCreateRequest(
        String name,
        int discountAmount,
        int totalQuantity
) {
}
