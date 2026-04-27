package com.example.commerce.infrastructure.persistence.coupon;

import com.example.commerce.domain.coupon.CouponUsage;
import com.example.commerce.domain.coupon.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CouponUsageRepositoryImpl implements CouponUsageRepository {

    private final CouponUsageJpaRepository jpaRepository;

    @Override
    public CouponUsage save(CouponUsage couponUsage) {
        return jpaRepository.save(couponUsage);
    }

    @Override
    public boolean existsByCouponIdAndMemberId(Long couponId, Long memberId) {
        return jpaRepository.existsByCouponIdAndMemberId(couponId, memberId);
    }

    @Override
    public List<CouponUsage> findAllByCouponId(Long couponId) {
        return jpaRepository.findAllByCouponId(couponId);
    }
}
