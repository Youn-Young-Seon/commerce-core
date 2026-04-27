package com.example.commerce.application.facade;

import com.example.commerce.application.service.StockService;
import com.example.commerce.domain.stock.exception.LockAcquisitionFailedException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 재고 차감에 대한 Redis 분산 락을 담당합니다.
 *
 * <p>낙관적 락의 한계:
 * - 충돌이 잦은 환경에서 maxAttempts 초과 → 일부 요청 실패 (retry storm)
 *
 * <p>분산 락 해결 방식:
 * - 락을 획득한 단 하나의 스레드만 재고를 읽고 차감
 * - 충돌 자체를 사전에 차단하므로 재시도 없이 안정적 처리
 *
 * <p>SRP 적용: 락 획득/해제 책임은 Facade가, 비즈니스 로직은 StockService가 담당
 */
@Component
@RequiredArgsConstructor
public class StockLockFacade {

    private static final String LOCK_KEY_PREFIX = "lock:stock:";
    private static final long WAIT_TIME_SECONDS = 3L;
    private static final long LEASE_TIME_SECONDS = 1L;

    private final RedissonClient redissonClient;
    private final StockService stockService;

    public void decrease(Long productId, int quantity) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + productId);

        try {
            boolean acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionFailedException(productId);
            }
            stockService.decrease(productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionFailedException(productId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
