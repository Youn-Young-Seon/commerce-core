package com.example.commerce.application.service;

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
 * 재고 차감 동시성 문제 재현 테스트.
 *
 * <p>race condition 발생 원리:
 * <pre>
 *   트랜잭션 A: SELECT quantity=100 → quantity - 1 = 99
 *   트랜잭션 B: SELECT quantity=100 → quantity - 1 = 99  ← 같은 값을 읽음
 *   트랜잭션 A: UPDATE quantity=99  COMMIT
 *   트랜잭션 B: UPDATE quantity=99  COMMIT  ← A의 차감이 유실됨 (Lost Update)
 * </pre>
 *
 * <p>이 테스트는 현재 의도적으로 실패합니다.
 * Step 3(낙관적 락) 적용 이후 통과하도록 설계되었습니다.
 */
class StockConcurrencyTest extends IntegrationTestSupport {

    private static final Long PRODUCT_ID = 1L;

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
    @DisplayName("동시 100명 요청 시 재고가 음수가 된다 (race condition 재현)")
    void 동시_100명_요청시_재고가_음수가_된다() throws InterruptedException {
        // given
        int initialStock = 100;
        int threadCount = 100;
        stockService.create(PRODUCT_ID, initialStock);

        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작 보장
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(32);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 준비될 때까지 대기
                    stockService.decrease(PRODUCT_ID, 1);
                } catch (Exception ignored) {
                    // race condition으로 인한 예외 무시 (차감 유실에 집중)
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 동시 출발
        endLatch.await();
        executor.shutdown();

        // then
        Stock stock = stockRepository.findByProductId(PRODUCT_ID).get();

        // 이 단언은 실패해야 정상 — race condition으로 인해 재고가 0이 아닌 양수로 남음
        // 즉, 100번 차감했으나 실제 차감된 횟수는 그보다 적음 (Lost Update 발생)
        assertThat(stock.getQuantity())
                .as("race condition으로 인해 재고가 정확히 차감되지 않아야 한다")
                .isNotEqualTo(0);
    }
}
