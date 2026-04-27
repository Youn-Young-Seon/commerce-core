package com.example.commerce.application.service;

import com.example.commerce.domain.coupon.Coupon;
import com.example.commerce.domain.coupon.CouponUsageRepository;
import com.example.commerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 테스트.
 *
 * <p>Redis DECR 원자성:
 * DECR은 단일 명령어로 읽기-감소-저장이 처리됩니다.
 * 별도의 락 없이도 동시 요청에서 정확한 수량 제어가 가능합니다.
 *
 * <pre>
 *   쿠폰 100장 → 200명 동시 요청
 *   → 정확히 100명만 발급 성공
 *   → DB CouponUsage 레코드 정확히 100건
 * </pre>
 */
class CouponConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Test
    @DisplayName("동시 200명 요청 시 선착순 100명만 쿠폰이 발급된다")
    void 동시_200명_요청시_선착순_100명만_쿠폰이_발급된다() throws InterruptedException {
        // given
        int totalQuantity = 100;
        int threadCount = 200;
        Coupon coupon = couponService.create("선착순 할인 쿠폰", 5_000, totalQuantity);
        Long couponId = coupon.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        AtomicInteger successCount = new AtomicInteger(0);

        // when - 각 스레드는 고유한 memberId를 가짐 (중복 발급 방지 검증과 별개)
        for (int i = 0; i < threadCount; i++) {
            final long memberId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issue(couponId, memberId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // CouponExhaustedException: 쿠폰 소진 — 정상적인 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        int issuedCount = couponUsageRepository.findAllByCouponId(couponId).size();

        assertThat(successCount.get())
                .as("Redis DECR 원자성으로 정확히 100명만 성공해야 한다")
                .isEqualTo(totalQuantity);

        assertThat(issuedCount)
                .as("DB에 저장된 CouponUsage 레코드도 정확히 100건이어야 한다")
                .isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("같은 회원이 동시에 쿠폰을 요청하면 1건만 발급된다")
    void 같은_회원이_동시에_쿠폰을_요청하면_1건만_발급된다() throws InterruptedException {
        // given
        int threadCount = 10;
        Coupon coupon = couponService.create("중복 방지 쿠폰", 1_000, 100);
        Long couponId = coupon.getId();
        Long memberId = 999L;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issue(couponId, memberId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        int issuedCount = couponUsageRepository.findAllByCouponId(couponId).size();

        assertThat(issuedCount)
                .as("같은 회원에게는 1건만 발급되어야 한다")
                .isEqualTo(1);
    }
}
