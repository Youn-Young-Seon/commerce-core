package com.example.commerce.application.service;

import com.example.commerce.domain.coupon.Coupon;
import com.example.commerce.domain.coupon.CouponRepository;
import com.example.commerce.domain.coupon.CouponUsage;
import com.example.commerce.domain.coupon.CouponUsageRepository;
import com.example.commerce.domain.coupon.exception.CouponAlreadyUsedException;
import com.example.commerce.domain.coupon.exception.CouponExhaustedException;
import com.example.commerce.domain.coupon.exception.CouponNotFoundException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String COUPON_REMAIN_KEY = "coupon:remain:";

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final RedissonClient redissonClient;

    @Transactional
    public Coupon create(String name, int discountAmount, int totalQuantity) {
        Coupon coupon = Coupon.of(name, discountAmount, totalQuantity);
        Coupon saved = couponRepository.save(coupon);

        // Redis에 잔여 수량 초기화 (DECR의 기준값)
        RAtomicLong remaining = redissonClient.getAtomicLong(COUPON_REMAIN_KEY + saved.getId());
        remaining.set(totalQuantity);

        return saved;
    }

    @Transactional
    public CouponUsage issue(Long couponId, Long memberId) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        if (couponUsageRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
            throw new CouponAlreadyUsedException();
        }

        // Redis DECR: 원자적 연산 — 별도의 락 없이 동시성 안전
        // DECR은 단일 명령어로 읽기-감소-저장이 원자적으로 처리됨
        RAtomicLong remaining = redissonClient.getAtomicLong(COUPON_REMAIN_KEY + couponId);
        long afterDecrement = remaining.decrementAndGet();

        if (afterDecrement < 0) {
            remaining.incrementAndGet(); // 음수 방지 보상
            throw new CouponExhaustedException(couponId);
        }

        return couponUsageRepository.save(CouponUsage.of(couponId, memberId));
    }

    @Transactional(readOnly = true)
    public Coupon getById(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
