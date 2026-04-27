package com.example.commerce.domain.coupon;

import java.util.List;

public interface CouponUsageRepository {

    CouponUsage save(CouponUsage couponUsage);

    boolean existsByCouponIdAndMemberId(Long couponId, Long memberId);

    List<CouponUsage> findAllByCouponId(Long couponId);
}
