package com.example.commerce.domain.coupon;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(Long id);

    Coupon save(Coupon coupon);
}
