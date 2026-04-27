package com.example.commerce.application.facade;

import com.example.commerce.application.service.StockService;
import com.example.commerce.domain.stock.Stock;
import com.example.commerce.domain.stock.StockRepository;
import com.example.commerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 분산 락을 이용한 재고 차감 동시성 테스트.
 *
 * <p>낙관적 락과의 차이:
 * - 낙관적 락: 충돌 발생 후 재시도 → maxAttempts 초과 시 일부 요청 실패
 * - 분산 락: 충돌 자체를 사전 차단 → 모든 요청이 순서대로 처리되어 quantity == 0 보장
 */
class StockLockFacadeTest extends IntegrationTestSupport {

    private static final Long PRODUCT_ID = 2L;

    @Autowired
    private StockLockFacade stockLockFacade;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @AfterEach
    void tearDown() {
        stockRepository.findByProductId(PRODUCT_ID).ifPresent(stock ->
                stockRepository.deleteById(stock.getId())
        );
    }

    @Test
    @DisplayName("동시 100명 요청 시 재고가 정확히 0이 된다 (분산 락 검증)")
    void 동시_100명_요청시_재고가_정확히_차감된다() throws InterruptedException {
        // given
        int initialStock = 100;
        int threadCount = 100;
        stockService.create(PRODUCT_ID, initialStock);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(32);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    stockLockFacade.decrease(PRODUCT_ID, 1);
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
        Stock stock = stockRepository.findByProductId(PRODUCT_ID).get();

        // 낙관적 락과 달리 분산 락은 모든 요청이 직렬화되어 처리됨
        // → 100번 차감 후 재고가 정확히 0이어야 한다
        assertThat(stock.getQuantity())
                .as("분산 락으로 모든 요청이 순서대로 처리되어 재고가 정확히 0이어야 한다")
                .isZero();
    }
}
