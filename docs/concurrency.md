# Concurrency Strategy

이 문서는 재고 차감 동시성 처리의 **점진적 진화 전략**을 단계별로 정의합니다.
각 단계는 독립적인 PR로 분리하여 사고 흐름이 히스토리에 남도록 합니다.

---

## 왜 점진적 진화인가

면접관 또는 코드 리뷰어가 PR 히스토리를 볼 때:

- "이 개발자가 문제를 어떻게 인식하는가"
- "어떤 CS 지식을 기반으로 해결하는가"
- "트레이드오프를 이해하고 있는가"

를 볼 수 있어야 합니다. 처음부터 완벽한 코드를 올리는 것보다
**문제를 발견하고 개선하는 과정**이 훨씬 강력한 포트폴리오가 됩니다.

---

## Step 1 — 순진한 구현

**브랜치**: `feature/stock-basic`
**목적**: 의도적으로 동시성 문제가 있는 코드로 시작. 베이스라인 확립.

```java
@Service
@Transactional
public class StockService {

    public void decrease(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
            .orElseThrow(() -> new StockNotFoundException(productId));

        // 문제: 동시 요청 시 race condition 발생 가능
        stock.decrease(quantity);
        stockRepository.save(stock);
    }
}
```

**PR 설명 포인트**:
- "단일 스레드 환경 기준 구현"
- "동시 요청 시 재고가 음수가 될 수 있음 — 다음 PR에서 검증"

---

## Step 2 — 문제 재현 테스트

**브랜치**: `feature/stock-concurrency-test`
**목적**: race condition을 테스트로 명확히 증명. CS 지식(가시성, 원자성) 주석으로 설명.

```java
@SpringBootTest
class StockConcurrencyTest {

    @Test
    @DisplayName("동시 100명 요청 시 재고가 음수가 된다 (race condition 재현)")
    void 동시_100명_요청시_재고가_음수가_된다() throws InterruptedException {
        // given
        int initialStock = 100;
        int threadCount = 100;
        stockSetup(productId, initialStock);

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(32);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    stockService.decrease(productId, 1);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        Stock stock = stockRepository.findByProductId(productId).get();

        // 이 테스트는 실패해야 정상 — race condition 증명
        // 실제로 재고가 0이 아닌 양수 또는 음수로 남을 수 있음
        assertThat(stock.getQuantity()).isNotEqualTo(0);
    }
}
```

**PR 설명 포인트**:
- race condition 발생 원인: DB read-modify-write 사이에 다른 트랜잭션 개입
- visibility 문제: 각 트랜잭션이 같은 초기값을 읽고 각자 차감
- 이 테스트는 Step 3 이후 통과해야 함

---

## Step 3 — 낙관적 락

**브랜치**: `feature/stock-optimistic-lock`
**목적**: JPA `@Version`으로 충돌 감지. 재시도 로직 포함.

```java
// domain
@Entity
public class Stock {

    @Version
    private int version; // JPA가 UPDATE 시 version 조건 자동 추가

    public void decrease(int amount) {
        if (this.quantity < amount) {
            throw new InsufficientStockException();
        }
        this.quantity -= amount;
    }
}

// application — 재시도 로직
@Service
public class StockService {

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void decrease(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
            .orElseThrow(() -> new StockNotFoundException(productId));
        stock.decrease(quantity);
    }
}
```

**동작 원리**:
```sql
-- JPA가 생성하는 UPDATE 쿼리
UPDATE stock
SET quantity = ?, version = version + 1
WHERE id = ? AND version = ?  -- version 불일치 시 0 rows affected → 예외 발생
```

**한계 (PR에 반드시 기술)**:
- 충돌이 잦은 환경에서 재시도 폭발 (retry storm)
- 트래픽이 몰리는 선착순 이벤트에서는 부적합
- maxAttempts 초과 시 사용자에게 실패 응답 → UX 저하

---

## Step 4 — Redis 분산 락

**브랜치**: `feature/stock-distributed-lock`
**목적**: Redisson 분산 락으로 고트래픽 환경 대응. 낙관적 락의 한계 해소.

```java
@Component
public class StockLockFacade {

    private final RedissonClient redissonClient;
    private final StockService stockService;

    public void decrease(Long productId, int quantity) {
        RLock lock = redissonClient.getLock("lock:stock:" + productId);

        try {
            // 3초 대기, 1초 후 자동 해제
            boolean acquired = lock.tryLock(3, 1, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionFailedException(productId);
            }
            stockService.decrease(productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockInterruptedException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**낙관적 락 vs 분산 락 비교**:

| 항목 | 낙관적 락 | 분산 락 |
|------|-----------|---------|
| 충돌 적을 때 | 빠름 (재시도 없음) | 락 오버헤드 |
| 충돌 많을 때 | 재시도 폭발 | 안정적 |
| 구현 복잡도 | 낮음 | 높음 (Redis 필요) |
| 분산 환경 | 가능 | 가능 |
| Redis 장애 시 | 영향 없음 | 주문 불가 |

**트레이드오프 (PR에 반드시 기술)**:
- Redis 장애 시 단일 장애점(SPOF) 가능성 → Redis Cluster 또는 Sentinel 필요
- 락 granularity: 현재 product 단위 → 향후 SKU 단위로 세분화 가능
- tryLock timeout 튜닝 필요 — 너무 짧으면 정상 요청 실패, 너무 길면 응답 지연

---

## Step 5 — 쿠폰 선착순 발급

**브랜치**: `feature/coupon-issue`
**목적**: 쿠폰 발급도 동시성 문제 존재. Redis atomic 연산으로 해결.

```java
// Redis DECR 명령은 원자적 — 락 없이도 안전
public void issue(Long couponId, Long memberId) {
    String key = "coupon:remain:" + couponId;

    Long remaining = redisTemplate.opsForValue().decrement(key);
    if (remaining == null || remaining < 0) {
        redisTemplate.opsForValue().increment(key); // 롤백
        throw new CouponExhaustedException(couponId);
    }

    // DB 저장은 비동기로 (이벤트 or 배치)
    couponRepository.save(CouponUsage.of(couponId, memberId));
}
```

---

## Step 6 — 포인트 트랜잭션

**브랜치**: `feature/point-transaction`
**목적**: 포인트 차감 실패 시 보상 트랜잭션. 이벤트 로그 패턴.

```java
// 이벤트 로그 방식 — 잔액 직접 수정 대신 이력 누적
public void use(Long memberId, Long orderId, int amount) {
    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new MemberNotFoundException(memberId));

    if (member.getPointBalance() < amount) {
        throw new InsufficientPointException();
    }

    // 잔액 차감 + 이력 동시 저장 (같은 트랜잭션)
    member.deductPoint(amount);
    pointHistoryRepository.save(PointHistory.use(memberId, orderId, amount));
}

// 주문 취소 시 보상
public void cancel(Long orderId) {
    PointHistory usage = pointHistoryRepository.findByOrderId(orderId);
    if (usage != null) {
        member.addPoint(usage.getAmount());
        pointHistoryRepository.save(PointHistory.cancel(usage));
    }
}
```

---

## 전체 진화 요약

```
Step 1  순진한 구현          → 의도적 문제 코드, 베이스라인
Step 2  테스트로 문제 증명   → CountDownLatch, race condition 재현
Step 3  낙관적 락            → @Version, 재시도, OOP 도메인 책임
Step 4  Redis 분산 락        → Redisson, 트레이드오프 분석
Step 5  쿠폰 선착순          → Redis DECR, 원자성
Step 6  포인트 트랜잭션      → 이벤트 로그, 보상 트랜잭션
Step 7  정산 배치            → Spring Batch Chunk, 멱등성
```

각 Step은 독립 브랜치 → PR → Merge 사이클로 진행합니다.
PR 설명 작성 가이드는 `docs/pr-template.md`를 참고하세요.
