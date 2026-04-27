# Architecture

## 레이어 구조

```
com.example.commerce
├── presentation          # Controller, DTO, GlobalExceptionHandler
│   ├── controller
│   ├── dto
│   │   ├── request
│   │   └── response
│   └── advice
│
├── application           # UseCase 조합, 트랜잭션 경계
│   ├── service
│   ├── facade
│   └── event
│
├── domain                # 핵심 비즈니스 로직, 순수 Java
│   ├── order
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── OrderStatus.java
│   │   └── OrderRepository.java       ← interface
│   ├── stock
│   │   ├── Stock.java
│   │   └── StockRepository.java       ← interface
│   ├── coupon
│   │   ├── Coupon.java
│   │   ├── CouponUsage.java
│   │   └── CouponRepository.java      ← interface
│   ├── point
│   │   ├── PointHistory.java
│   │   └── PointRepository.java       ← interface
│   └── settlement
│       ├── Settlement.java
│       └── SettlementRepository.java  ← interface
│
└── infrastructure        # 외부 시스템 연동, domain interface 구현
    ├── persistence       # JPA Entity, Repository 구현체
    ├── cache             # Redis 연동
    ├── batch             # Spring Batch Job, Step 구성
    └── messaging         # 이벤트 발행
```

---

## 의존성 방향 규칙

```
presentation → application → domain ← infrastructure
```

### 절대 금지

- `domain`이 `infrastructure` 패키지를 import하는 것
- `domain`이 Spring 어노테이션에 의존하는 것 (`@Component`, `@Service` 등)
- `presentation`이 `domain` 객체를 직접 반환하는 것 (항상 DTO로 변환)

### 올바른 의존 흐름 예시

```java
// domain — 순수 Java, 외부 의존 없음
public class Stock {
    private int quantity;
    private int version; // 낙관적 락용

    public void decrease(int amount) {
        if (this.quantity < amount) {
            throw new InsufficientStockException();
        }
        this.quantity -= amount;
    }
}

// domain — Repository는 interface만
public interface StockRepository {
    Stock findByProductId(Long productId);
    Stock save(Stock stock);
}

// infrastructure — interface 구현
@Repository
public class StockJpaRepository implements StockRepository {
    // JPA 구현
}

// application — 도메인 조합, 트랜잭션 경계
@Service
@Transactional
public class OrderFacade {
    private final StockRepository stockRepository;  // domain interface 주입
    private final OrderRepository orderRepository;

    public void placeOrder(PlaceOrderCommand command) {
        Stock stock = stockRepository.findByProductId(command.productId());
        stock.decrease(command.quantity());          // 비즈니스 로직은 도메인 객체 안에
        // ...
    }
}
```

---

## DB 스키마

### 핵심 테이블

```sql
-- 회원
CREATE TABLE member (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    point_balance INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 주문
CREATE TABLE orders (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT       NOT NULL REFERENCES member(id),
    status         VARCHAR(30)  NOT NULL,  -- PENDING, CONFIRMED, CANCELLED
    total_price    INT          NOT NULL,
    discount_price INT          NOT NULL DEFAULT 0,
    final_price    INT          NOT NULL,
    ordered_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 주문 상품
CREATE TABLE order_item (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    unit_price INT    NOT NULL
);

-- 재고 (version: 낙관적 락)
CREATE TABLE stock (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    quantity   INT    NOT NULL,
    version    INT    NOT NULL DEFAULT 0
);

-- 쿠폰 사용 내역
CREATE TABLE coupon_usage (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    coupon_id       BIGINT NOT NULL,
    discount_amount INT    NOT NULL
);

-- 포인트 이력 (이벤트 로그 방식)
CREATE TABLE point_history (
    id        BIGSERIAL PRIMARY KEY,
    member_id BIGINT      NOT NULL REFERENCES member(id),
    order_id  BIGINT      REFERENCES orders(id),
    type      VARCHAR(20) NOT NULL,  -- EARN, USE, CANCEL
    amount    INT         NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 정산 (Spring Batch upsert 대상)
CREATE TABLE settlement (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT      NOT NULL REFERENCES orders(id),
    settled_date  DATE        NOT NULL,
    amount        INT         NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, DONE
    UNIQUE (order_id)
);
```

---

## 예외 계층 구조

```java
// 최상위 비즈니스 예외
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
}

// 도메인별 예외
public class InsufficientStockException extends BusinessException { }
public class CouponAlreadyUsedException extends BusinessException { }
public class InsufficientPointException extends BusinessException { }

// 일괄 처리
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) { }
}
```

---

## 할인 정책 — OCP 적용 예시

새로운 할인 정책 추가 시 기존 코드 수정 없이 확장 가능하도록 설계합니다.

```java
// 할인 정책 인터페이스
public interface DiscountPolicy {
    int calculate(Order order);
}

// 구현체들
public class CouponDiscountPolicy implements DiscountPolicy { }
public class PointDiscountPolicy implements DiscountPolicy { }
public class RateDiscountPolicy implements DiscountPolicy { }

// 조합 (Composite 패턴)
public class CompositeDiscountPolicy implements DiscountPolicy {
    private final List<DiscountPolicy> policies;

    public int calculate(Order order) {
        return policies.stream()
            .mapToInt(p -> p.calculate(order))
            .sum();
    }
}
```
