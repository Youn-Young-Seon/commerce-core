package com.example.commerce.infrastructure.persistence.coupon;

import com.example.commerce.domain.coupon.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponUsageJpaRepository extends JpaRepository<CouponUsage, Long> {

    boolean existsByCouponIdAndMemberId(Long couponId, Long memberId);

    List<CouponUsage> findAllByCouponId(Long couponId);
}
