package com.example.commerce.presentation.dto.response;

import com.example.commerce.domain.coupon.Coupon;

public record CouponResponse(
        Long id,
        String name,
        int discountAmount,
        int totalQuantity
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountAmount(),
                coupon.getTotalQuantity()
        );
    }
}
