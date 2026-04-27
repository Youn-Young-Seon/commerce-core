# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 프로젝트 목적

이커머스 주문 플랫폼 포트폴리오 프로젝트.

**핵심 목표**: 아임웹 등 제품 회사 백엔드 채용 과제 대응.
단순히 "돌아가는 코드"가 아니라, **OOP/SOLID 원칙 준수**와 **CS 기반 점진적 코드 진화**를
커밋/PR 히스토리로 명확하게 보여주는 것이 이 프로젝트의 핵심 전략입니다.

면접관이 PR 히스토리를 보면서 개발자의 사고 흐름을 따라갈 수 있어야 합니다.

---

## 기술 스택

| 영역 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| RDB | PostgreSQL |
| Cache | Redis (Redisson) |
| Batch | Spring Batch |
| Test | JUnit5 + Testcontainers |
| Docs | Swagger (springdoc-openapi) |
| CI | GitHub Actions |

---

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.example.commerce.domain.stock.StockTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.example.commerce.StockConcurrencyTest.동시_100명_요청시_재고가_정확히_차감된다"

# 통합 테스트만 실행 (Testcontainers 필요)
./gradlew test -Pintegration

# 빌드 없이 테스트
./gradlew test --rerun-tasks
```

> Testcontainers 통합 테스트는 Docker가 실행 중이어야 합니다.

---

## 아키텍처

**레이어드 아키텍처**, 의존 방향 엄수:

```
presentation → application → domain ← infrastructure
```

패키지 구조 (`com.example.commerce`):

```
├── presentation/          # Controller, DTO (request/response), GlobalExceptionHandler
├── application/           # UseCase 조합, 트랜잭션 경계 (Service, Facade, Event)
├── domain/                # 순수 Java 비즈니스 로직 + Repository interface
│   ├── order/
│   ├── stock/
│   ├── coupon/
│   ├── point/
│   └── settlement/
└── infrastructure/        # JPA 구현체, Redis, Spring Batch, 이벤트 발행
    ├── persistence/
    ├── cache/
    ├── batch/
    └── messaging/
```

**핵심 의존성 규칙** (위반 금지):
- `domain`은 `infrastructure` 패키지를 import하지 않습니다
- `domain`은 Spring 어노테이션(`@Component`, `@Service` 등)에 의존하지 않습니다
- `presentation`은 domain 객체를 직접 반환하지 않고 항상 DTO로 변환합니다
- Repository는 `domain`에 interface, 구현체는 `infrastructure/persistence`에 위치합니다

---

## 핵심 구현 전략 — 점진적 진화

각 기능은 반드시 아래 단계를 순서대로 거쳐 **각 단계를 별도 PR로 분리**합니다.

| Step | 브랜치 | 목적 |
|------|--------|------|
| 1 | `feature/stock-basic` | 의도적 문제 코드로 베이스라인 확립 |
| 2 | `feature/stock-concurrency-test` | CountDownLatch로 race condition 재현 |
| 3 | `feature/stock-optimistic-lock` | `@Version` + `@Retryable` 적용 |
| 4 | `feature/stock-distributed-lock` | Redisson 분산 락, 트레이드오프 분석 |
| 5 | `feature/coupon-issue` | Redis DECR 원자적 선착순 발급 |
| 6 | `feature/point-transaction` | 이벤트 로그 패턴, 보상 트랜잭션 |
| 7 | `feature/settlement-batch` | Spring Batch Chunk, 멱등성 |

상세 코드 예시 → `docs/concurrency.md`

---

## 코드 작성 규칙

### 도메인 객체

- Anemic Domain Model 금지 — 비즈니스 로직은 도메인 객체 안에
- 상태 변경 메서드는 도메인 객체 내부에
- 생성자 대신 정적 팩토리 메서드 (`Order.place(...)`)

### 예외 처리

- 커스텀 예외 계층: `BusinessException` → `InsufficientStockException`, `CouponAlreadyUsedException`, `InsufficientPointException`
- `@RestControllerAdvice`(`GlobalExceptionHandler`)에서 `BusinessException` 일괄 처리
- 매직 넘버 금지 — 상수 또는 enum으로

### 테스트

- 단위 테스트: Mock 사용, 빠르게
- 통합 테스트: Testcontainers로 실제 DB/Redis 사용
- 동시성 테스트: `CountDownLatch` + `ExecutorService`
- 테스트 메서드명: 한글로 의도 명확히 (`동시_100명_요청시_재고가_정확히_차감된다`)

---

## PR / 커밋 규칙

### 커밋 메시지 (Conventional Commits)

```
feat: 재고 차감 기본 구현
test: 재고 동시성 문제 재현 테스트 추가
refactor: 낙관적 락 적용으로 동시성 처리
perf: Redis 분산 락으로 고트래픽 대응
```

### PR 필수 포함 항목

- 변경 배경 (왜 이 PR가 필요한가)
- 해결 방법 (어떤 기술적 선택을 했는가)
- 트레이드오프 (이 선택의 한계는 무엇인가)
- 테스트 결과

PR 작성 예시 → `docs/pr-template.md`

---

## 참고 문서

| 파일 | 내용 |
|------|------|
| `docs/architecture.md` | 레이어 구조, 의존성 규칙 코드 예시, DB 스키마, 할인 정책 OCP 설계 |
| `docs/concurrency.md` | 동시성 전략 Step별 코드 예시 및 트레이드오프 |
| `docs/pr-template.md` | PR 작성 템플릿, 각 Step별 PR 예시, 브랜치 전략 |
