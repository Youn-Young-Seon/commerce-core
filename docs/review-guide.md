# 코드 리뷰 가이드

이 문서는 PR 히스토리를 순서대로 따라가며 코드를 검토하는 방법을 안내합니다.
각 단계에서 **왜 이 순서인지**, **무엇을 봐야 하는지**, **소스가 어떻게 진화했는지**를 설명합니다.

---

## 전체 전략 — 왜 점진적으로 작성했는가

이 프로젝트는 "처음부터 완벽한 코드"가 아니라 **문제를 발견하고 개선하는 과정**을 보여주기 위해 설계되었습니다.

```
문제 인식 → 테스트로 증명 → 해결 → 한계 인식 → 더 나은 해결
```

면접관이 PR 히스토리를 볼 때 확인하는 것:
- 이 개발자가 동시성 문제를 이론이 아닌 테스트로 증명할 수 있는가
- 기술 선택의 트레이드오프를 알고 있는가
- OOP/SOLID 원칙을 코드에 어떻게 녹여내는가

---

## PR #1 — `feature/stock-basic`

### 왜 이것부터 시작했는가

모든 이커머스 시스템의 가장 기본 동작인 **재고 차감**을 가장 단순하게 구현합니다.
의도적으로 동시성 처리를 제외합니다. "단순 구현으로 시작하고 문제가 생기면 고친다"는 흐름을 만들기 위해서입니다.

### 핵심적으로 봐야 하는 부분

**1. 레이어드 아키텍처 의존 방향**

```
presentation → application → domain ← infrastructure
```

`domain/stock/Stock.java`는 `import`를 확인해보면 `jakarta.persistence.*` 외에
Spring 패키지가 전혀 없습니다. 도메인은 순수 Java입니다.

**2. 비즈니스 로직의 위치 (OOP)**

```java
// ❌ 나쁜 예: Service에 로직이 있음
stock.setQuantity(stock.getQuantity() - quantity);

// ✅ 이 프로젝트: 도메인 객체 내부에 로직 캡슐화
stock.decrease(quantity); // InsufficientStockException도 여기서 발생
```

`Stock.decrease()`가 재고 부족 예외를 직접 던집니다.
Service가 재고를 직접 조작하지 않습니다.

**3. 의존성 역전 원칙 (DIP)**

```
domain/stock/StockRepository.java     ← interface (도메인에 위치)
infrastructure/.../StockRepositoryImpl.java ← 구현체 (인프라에 위치)
```

`StockService`는 `StockRepository` interface만 알고, 구현체(`StockRepositoryImpl`)를 모릅니다.

**4. DTO 변환 계층**

`StockController`가 `Stock` 도메인 객체를 직접 반환하지 않고 `StockResponse.from(stock)`으로 변환합니다.
도메인 모델과 API 응답 구조를 분리합니다.

### 소스 진화 포인트

이 PR에서 확립한 패턴(정적 팩토리, Repository interface, DTO 변환)은 이후 모든 도메인(Coupon, Point, Settlement)에서 그대로 반복됩니다.

---

## PR #2 — `feature/stock-concurrency-test`

### 왜 바로 해결하지 않고 테스트 먼저인가

"동시성 문제가 있다"는 말은 누구나 할 수 있습니다.
**테스트 코드로 실제 발생을 증명하는 것**과 구두로 설명하는 것은 완전히 다른 수준의 역량을 보여줍니다.

### 핵심적으로 봐야 하는 부분

**1. `CountDownLatch` 동시성 패턴**

```java
CountDownLatch startLatch = new CountDownLatch(1);  // 출발 신호
CountDownLatch endLatch = new CountDownLatch(threadCount); // 완료 대기

// 모든 스레드를 준비 상태로 만들고
startLatch.countDown(); // 동시에 출발

endLatch.await(); // 전부 끝날 때까지 기다림
```

`startLatch`가 없으면 스레드들이 순서대로 실행되어 race condition이 재현되지 않습니다.

**2. H2가 아닌 Testcontainers (실제 PostgreSQL)**

```java
// IntegrationTestSupport.java
static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
```

H2는 트랜잭션 격리 수준이 달라 race condition이 재현되지 않을 수 있습니다.
운영 환경과 동일한 PostgreSQL로 테스트합니다.

**3. 테스트의 의미**

```java
// 이 단언이 통과(quantity != 0)한다는 것 자체가 race condition 증명
assertThat(stock.getQuantity()).isNotEqualTo(0);
```

100번 차감했는데 재고가 0이 아닌 양수로 남아 있음 = Lost Update 발생.

### 소스 진화 포인트

`IntegrationTestSupport`가 이 PR에서 처음 도입됩니다.
이후 PR #3, #5, #6, #7의 통합 테스트가 모두 이 클래스를 상속하여 컨테이너를 재사용합니다.

---

## PR #3 — `feature/stock-optimistic-lock`

### 왜 낙관적 락을 먼저 적용했는가

비관적 락(`SELECT FOR UPDATE`)이나 Redis 락보다 **가장 적은 인프라 추가로 해결**할 수 있는 방법입니다.
단순한 것부터 시도하고 한계가 명확해질 때 더 복잡한 해결책으로 가는 것이 올바른 순서입니다.

### 핵심적으로 봐야 하는 부분

**1. `@Version` — JPA가 생성하는 SQL 변화**

```java
// Stock.java에 추가된 단 한 줄
@Version
private int version;
```

이 한 줄이 JPA의 UPDATE 쿼리를 바꿉니다:

```sql
-- Before (@Version 없음)
UPDATE stock SET quantity = ? WHERE id = ?

-- After (@Version 있음)
UPDATE stock SET quantity = ?, version = version + 1
WHERE id = ? AND version = ?   -- version 불일치 시 0 rows → 예외
```

**2. 재시도 로직 위치 (SRP)**

```java
// StockService.java — 재시도는 Service가 담당, Stock은 모름
@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
@Transactional
public void decrease(Long productId, int quantity) { ... }
```

`Stock` 도메인 객체는 재시도를 모릅니다. 재시도 정책은 애플리케이션 관심사입니다.

**3. 테스트 assertion의 역전**

PR #2에서 `isNotEqualTo(0)` → PR #3에서 `isEqualTo(initialStock - successCount.get())`으로 변경됩니다.

```java
// Lost Update 없음 검증
// successCount = 실제 성공한 차감 횟수
assertThat(stock.getQuantity()).isEqualTo(initialStock - successCount.get());
```

성공한 차감과 실제 재고가 정확히 일치 = Lost Update 제거.
단, `successCount < threadCount`일 수 있음 = 낙관적 락의 한계.

### 소스 진화 포인트

`Stock.java`에 `@Version` 필드 1개, `StockService.java`에 `@Retryable` 추가가 전부입니다.
기존 로직을 전혀 변경하지 않고 어노테이션만으로 동시성 처리를 추가합니다.

---

## PR #4 — `feature/stock-distributed-lock`

### 왜 낙관적 락 다음에 분산 락인가

PR #3 테스트에서 `maxAttempts=3` 초과로 일부 요청이 실패함을 확인했습니다.
"선착순 이벤트처럼 충돌이 집중되는 상황"에서는 낙관적 락이 부적합합니다.
이 한계를 직접 테스트로 보여준 뒤 해결책을 도입합니다.

### 핵심적으로 봐야 하는 부분

**1. `StockLockFacade` — SRP 적용**

```java
// StockLockFacade.java — 락 획득/해제만 담당
public void decrease(Long productId, int quantity) {
    RLock lock = redissonClient.getLock("lock:stock:" + productId);
    try {
        lock.tryLock(3, 1, TimeUnit.SECONDS);
        stockService.decrease(productId, quantity); // 비즈니스 로직은 위임
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

`StockService`는 락을 모릅니다. 락의 존재가 비즈니스 로직을 오염시키지 않습니다.

**2. `StockController`의 변화**

```java
// Before (PR #1)
stockService.decrease(productId, request.quantity());

// After (PR #4)
stockLockFacade.decrease(productId, request.quantity());
```

Controller는 Facade로 교체만 합니다. 락 로직이 비즈니스 레이어로 스며들지 않습니다.

**3. 낙관적 락 vs 분산 락 테스트 결과 비교**

| 테스트 | 낙관적 락 결과 | 분산 락 결과 |
|--------|--------------|------------|
| 동시 100 요청 | `successCount < 100` (일부 실패) | `quantity == 0` (전부 성공) |

이 차이를 두 테스트 파일을 나란히 보면 명확하게 확인할 수 있습니다.

### 소스 진화 포인트

PR #3까지의 `StockService.decrease()`에 있던 `@Retryable`은 그대로 유지됩니다.
분산 락이 적용된 경우 충돌 자체가 없으므로 재시도가 발생하지 않지만,
락 획득에 실패했을 때 예외 처리 경로로 남겨둡니다.

---

## PR #5 — `feature/coupon-issue`

### 왜 재고 다음에 쿠폰인가

재고와 동일하게 "동시에 여러 명이 요청하면 초과 발급될 수 있다"는 동시성 문제가 있습니다.
단, 해결책이 다릅니다. Redis의 **원자적 명령어**를 활용합니다.

### 핵심적으로 봐야 하는 부분

**1. Redis DECR의 원자성**

```java
// CouponService.java
RAtomicLong remaining = redissonClient.getAtomicLong("coupon:remain:" + couponId);
long afterDecrement = remaining.decrementAndGet(); // 단일 명령어, 원자적

if (afterDecrement < 0) {
    remaining.incrementAndGet(); // 보상
    throw new CouponExhaustedException(couponId);
}
```

`decrementAndGet()`은 Redis 서버에서 단일 명령어로 실행됩니다.
분산 락이 "충돌을 차단"하는 방식이라면, DECR은 "충돌이 불가능한 구조"입니다.

**2. 이중 방어선**

```java
// 1차: 애플리케이션 레벨 (중복 발급 방지)
if (couponUsageRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
    throw new CouponAlreadyUsedException();
}

// 2차: DB 레벨 (최후 방어)
// CouponUsage.java
@UniqueConstraint(columnNames = {"coupon_id", "member_id"})
```

애플리케이션 검사가 실패해도 DB 제약조건이 최종 방어합니다.

**3. 테스트 시나리오 — 200명이 100장 요청**

```java
// 핵심 단언 2가지
assertThat(successCount.get()).isEqualTo(totalQuantity);   // 정확히 100명만 성공
assertThat(issuedCount).isEqualTo(totalQuantity);          // DB에도 정확히 100건
```

Redis 수량과 DB 레코드 수가 일치함 = Redis-DB 정합성 확인.

### 소스 진화 포인트

재고(분산 락)와 쿠폰(DECR)은 같은 문제를 다른 방법으로 해결합니다.
두 접근법의 적합한 상황을 비교하며 읽으면 좋습니다:
- 재고: 락이 필요 (차감 후 DB 저장까지 원자성 필요)
- 쿠폰: DECR으로 충분 (수량 차감 자체가 원자적)

---

## PR #6 — `feature/point-transaction`

### 왜 포인트에 이벤트 로그 패턴인가

포인트는 주문과 연동됩니다. 주문 취소 시 차감된 포인트를 정확히 복구해야 하는데,
단순히 잔액만 저장하는 방식은 "왜 이 잔액이 됐는가"를 추적할 수 없습니다.
이벤트 로그 패턴으로 모든 변동을 이력으로 남겨 보상 트랜잭션을 가능하게 합니다.

### 핵심적으로 봐야 하는 부분

**1. 이벤트 로그 패턴 — `PointHistory`의 정적 팩토리**

```java
// 타입별로 의미가 명확한 생성 메서드
PointHistory.earn(memberId, orderId, amount);   // 적립
PointHistory.use(memberId, orderId, amount);    // 사용
PointHistory.cancel(memberId, orderId, amount); // 보상
```

`new PointHistory(EARN, ...)` 대신 의도를 명확히 드러내는 정적 팩토리를 사용합니다.

**2. 원자성 — 잔액 변경 + 이력 저장이 같은 트랜잭션**

```java
// PointService.use() — @Transactional
member.deductPoint(amount);        // 잔액 차감
memberRepository.save(member);
pointHistoryRepository.save(PointHistory.use(...)); // 이력 저장
// → 둘 중 하나만 성공하는 상황 없음
```

**3. 보상 트랜잭션과 멱등성**

```java
// PointService.cancel()
pointHistoryRepository.findByOrderIdAndType(orderId, PointHistoryType.USE)
    .ifPresent(history -> {
        // USE 이력이 있을 때만 복구 — 포인트 미사용 주문 취소는 no-op
        member.addPoint(history.getAmount());
        pointHistoryRepository.save(PointHistory.cancel(...));
    });
```

`ifPresent`로 포인트를 사용하지 않은 주문 취소를 멱등하게 처리합니다.

**4. `Member.deductPoint()`의 캡슐화**

```java
// Member.java
public void deductPoint(int amount) {
    if (this.pointBalance < amount) {
        throw new InsufficientPointException(); // 도메인 객체가 직접 예외 발생
    }
    this.pointBalance -= amount;
}
```

잔액 검증 로직이 Service가 아닌 도메인 객체 안에 있습니다.

### 소스 진화 포인트

PR #1의 `Stock.decrease()` 패턴(도메인 캡슐화, 예외 발생)이 `Member.deductPoint()`에서 동일하게 반복됩니다.
PR #1에서 확립한 패턴이 일관되게 적용되고 있음을 확인할 수 있습니다.

---

## PR #7 — `feature/settlement-batch`

### 왜 마지막이 배치인가

배치는 앞서 구축된 도메인들(Order, Settlement)을 **대량으로 처리**하는 레이어입니다.
도메인 로직이 먼저 안정화된 뒤 배치로 묶는 것이 자연스러운 순서입니다.

### 핵심적으로 봐야 하는 부분

**1. Chunk-oriented Processing 구조**

```
Reader (100건 읽기) → Processor (변환/필터) → Writer (저장) → 반복
```

`SettlementJobConfig.java`에서 각 Bean의 역할이 명확히 분리되어 있습니다.

**2. 멱등성 — Processor의 null 반환**

```java
// SettlementJobConfig.java
.processor(order -> {
    if (settlementJpaRepository.existsByOrderId(order.getId())) {
        return null; // Spring Batch: null이면 해당 item을 Writer로 넘기지 않음
    }
    return Settlement.of(order.getId(), settledDate, order.getFinalPrice());
})
```

동일한 Job을 재실행해도 이미 정산된 주문은 건너뜁니다.
`Settlement` 테이블의 `UNIQUE(order_id)` 제약조건이 이중 방어선입니다.

**3. `@StepScope` + `jobParameters`**

```java
@StepScope // Job 실행 시점에 Bean 생성 → JobParameters 주입 가능
public ItemProcessor<Order, Settlement> settlementProcessor(
        @Value("#{jobParameters['settledDate']}") String settledDate) {
```

정산 기준일을 외부에서 주입받아 과거 날짜 재정산도 가능합니다.

**4. 테스트에서 `@BeforeEach` 데이터 초기화**

```java
@BeforeEach
void setUp() {
    settlementJpaRepository.deleteAllInBatch();
    orderJpaRepository.deleteAllInBatch();
    jobRepositoryTestUtils.removeJobExecutions(); // Batch 메타데이터도 초기화
}
```

Testcontainers는 테스트 클래스 단위로 컨테이너를 공유하므로 테스트 간 데이터 격리가 필요합니다.

### 소스 진화 포인트

`Order` 도메인이 이 PR에서 처음 등장합니다. 실제 주문 처리 플로우는 없지만,
정산 배치가 필요로 하는 최소한의 인터페이스(`status`, `finalPrice`)만 갖추고 있습니다.
YAGNI(You Aren't Gonna Need It) 원칙 — 필요한 것만 만듭니다.

---

## 전체 소스 진화 요약

```
PR #1  Stock { id, productId, quantity }
            ↓ decrease() 캡슐화

PR #3  Stock { id, productId, quantity, version }
            ↓ @Version 추가 (필드 1개, 로직 변경 없음)

PR #4  StockLockFacade 추가
       StockController → StockService 대신 StockLockFacade 호출
            ↓ 기존 StockService 코드 변경 없음

PR #5  Coupon, CouponUsage 도메인 추가 (PR #1 패턴 반복)

PR #6  Member { id, email, pointBalance }
       PointHistory { memberId, orderId, type, amount } (이벤트 로그)
            ↓ PR #1의 도메인 캡슐화 패턴 반복

PR #7  Order { id, memberId, status, finalPrice }
       Settlement { orderId, settledDate, amount, status }
       SettlementJobConfig (Reader → Processor → Writer)
```

---

## 로컬 실행 방법

### 사전 요구사항

- Java 17 (Temurin)
- Docker (Testcontainers용)
- `gradle.properties`의 `org.gradle.java.home` 확인

### 인프라 실행

```bash
docker-compose up -d
```

### 단위 테스트 (Docker 불필요)

```bash
./gradlew test --tests "com.example.commerce.domain.stock.StockTest"
```

### 통합 테스트 (Docker 필요)

```bash
# Step 2: race condition 재현
./gradlew test --tests "com.example.commerce.application.service.StockConcurrencyTest"

# Step 3: 낙관적 락
./gradlew test --tests "com.example.commerce.application.service.StockConcurrencyTest"

# Step 4: 분산 락
./gradlew test --tests "com.example.commerce.application.facade.StockLockFacadeTest"

# Step 5: 쿠폰 선착순
./gradlew test --tests "com.example.commerce.application.service.CouponConcurrencyTest"

# Step 6: 포인트 트랜잭션
./gradlew test --tests "com.example.commerce.application.service.PointServiceTest"

# Step 7: 정산 배치
./gradlew test --tests "com.example.commerce.infrastructure.batch.SettlementJobTest"

# 전체 테스트
./gradlew test
```
